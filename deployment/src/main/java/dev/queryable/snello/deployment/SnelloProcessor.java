package dev.queryable.snello.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import dev.queryable.snello.runtime.SnelloTypes;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.rest.data.panache.deployment.ResourceMetadata;
import io.quarkus.rest.data.panache.deployment.RestDataResourceBuildItem;
import io.quarkus.rest.data.panache.deployment.properties.MethodProperties;
import io.quarkus.rest.data.panache.deployment.properties.ResourceProperties;
import io.quarkus.rest.data.panache.deployment.properties.ResourcePropertiesBuildItem;
import io.quarkus.rest.data.panache.deployment.utils.EntityTypeUtils;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceGizmoAdaptor;

class SnelloProcessor {

    private static final Logger LOG = Logger.getLogger(SnelloProcessor.class);

    private static final String FEATURE = "snello";

    private static final DotName ENTITY = DotName.createSimple("jakarta.persistence.Entity");
    private static final DotName SNELLO_EXCLUDE = DotName.createSimple("dev.queryable.snello.runtime.SnelloExclude");
    private static final DotName TABLE = DotName.createSimple("jakarta.persistence.Table");
    private static final DotName ID = DotName.createSimple("jakarta.persistence.Id");
    private static final DotName TRANSIENT = DotName.createSimple("jakarta.persistence.Transient");
    private static final List<DotName> CALLBACK_ANNOTATIONS = List.of(
            DotName.createSimple("dev.queryable.snello.runtime.PreFetch"),
            DotName.createSimple("dev.queryable.snello.runtime.PostFetch"),
            DotName.createSimple("dev.queryable.snello.runtime.PreList"),
            DotName.createSimple("dev.queryable.snello.runtime.PostList"),
            DotName.createSimple("dev.queryable.snello.runtime.PrePersist"),
            DotName.createSimple("dev.queryable.snello.runtime.PostPersist"),
            DotName.createSimple("dev.queryable.snello.runtime.PreUpdate"),
            DotName.createSimple("dev.queryable.snello.runtime.PostUpdate"),
            DotName.createSimple("dev.queryable.snello.runtime.PreDelete"),
            DotName.createSimple("dev.queryable.snello.runtime.PostDelete"));
    private static final Set<DotName> NON_FILTERABLE = Set.of(
            DotName.createSimple("jakarta.persistence.ManyToOne"),
            DotName.createSimple("jakarta.persistence.OneToOne"),
            DotName.createSimple("jakarta.persistence.OneToMany"),
            DotName.createSimple("jakarta.persistence.ManyToMany"),
            DotName.createSimple("jakarta.persistence.ElementCollection"),
            DotName.createSimple("jakarta.persistence.Embedded"),
            DotName.createSimple("jakarta.persistence.EmbeddedId"));

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerRuntime() {
        return AdditionalBeanBuildItem.unremovableOf("dev.queryable.snello.runtime.SnelloEngine");
    }

    @BuildStep
    void registerCallbackMethods(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods) {
        for (DotName annotation : CALLBACK_ANNOTATIONS) {
            for (AnnotationInstance callback : combinedIndex.getIndex().getAnnotations(annotation)) {
                if (callback.target().kind() == AnnotationTarget.Kind.METHOD) {
                    reflectiveMethods.produce(new ReflectiveMethodBuildItem(
                            "Snello REST lifecycle callback", callback.target().asMethod()));
                }
            }
        }
    }

    @BuildStep
    void generateResources(CombinedIndexBuildItem combinedIndex,
            SnelloBuildTimeConfig config,
            BuildProducer<GeneratedJaxRsResourceBuildItem> resourceProducer,
            BuildProducer<GeneratedBeanBuildItem> beanProducer,
            BuildProducer<RestDataResourceBuildItem> restDataResources,
            BuildProducer<ResourcePropertiesBuildItem> resourceProperties) {
        IndexView index = combinedIndex.getIndex();
        ClassOutput jaxRsOutput = new GeneratedJaxRsResourceGizmoAdaptor(resourceProducer);
        ClassOutput beanOutput = new GeneratedBeanGizmoAdaptor(beanProducer);
        String basePath = normalize(config.basePath());

        for (AnnotationInstance annotation : index.getAnnotations(ENTITY)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo entity = annotation.target().asClass();
            if (Modifier.isAbstract(entity.flags()) || entity.hasDeclaredAnnotation(SNELLO_EXCLUDE)) {
                continue;
            }
            String entityName = entity.name().toString();
            String idType = idType(index, entity);
            if (idType == null) {
                LOG.warnf("Skipping entity %s: no @Id field found", entityName);
                continue;
            }
            String path = basePath + "/" + tableName(entity);
            String resourceName = entityName + "SnelloResource";
            String adapterName = entityName + "SnelloAdapter";

            generateResource(jaxRsOutput, resourceName, entityName, path, queryParameters(entity, index));            generateRestDataAdapter(beanOutput, adapterName, entityName);

            restDataResources.produce(new RestDataResourceBuildItem(
                    new ResourceMetadata(adapterName, adapterName, null, entityName, idType,
                            EntityTypeUtils.getEntityFields(index, entityName))));

            MethodProperties listProperties = new MethodProperties(false, "", new String[0], List.of());
            ResourceProperties properties = new ResourceProperties(
                    true, path, true, false, "",
                    new String[0], false, List.of(), Map.of("list", listProperties));
            resourceProperties.produce(new ResourcePropertiesBuildItem(adapterName, properties));
        }
    }

    private String normalize(String basePath) {
        String path = basePath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "/".equals(path) ? "" : path;
    }

    private String tableName(ClassInfo entity) {
        AnnotationInstance table = entity.declaredAnnotation(TABLE);
        if (table != null) {
            AnnotationValue name = table.value("name");
            if (name != null && !name.asString().isBlank()) {
                return name.asString();
            }
        }
        return entity.simpleName().toLowerCase(Locale.ROOT);
    }

    private String idType(IndexView index, ClassInfo entity) {
        for (ClassInfo current = entity; current != null; current = index.getClassByName(current.superName())) {
            for (FieldInfo field : current.fields()) {
                if (field.hasAnnotation(ID)) {
                    return field.type().name().toString();
                }
            }
            if (current.superName() == null) {
                return null;
            }
        }
        return null;
    }

    private void generateResource(ClassOutput classOutput, String resourceName, String entityName, String path,
            Map<String, String> queryParameters) {
        try (ClassCreator creator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(resourceName)
                .superClass("dev.queryable.snello.runtime.SnelloGeneratedResource")
                .build()) {
            creator.addAnnotation(Path.class).addValue("value", path);
            creator.addAnnotation(Produces.class).addValue("value", new String[] { MediaType.APPLICATION_JSON });
            creator.addAnnotation(Consumes.class).addValue("value", new String[] { MediaType.APPLICATION_JSON });

            MethodCreator constructor = creator.getMethodCreator("<init>", void.class);
            constructor.setParameterNames(new String[0]);
            constructor.invokeSpecialMethod(
                    MethodDescriptor.ofConstructor(
                            "dev.queryable.snello.runtime.SnelloGeneratedResource", Class.class),
                    constructor.getThis(), constructor.loadClass(entityName));
            constructor.returnVoid();

            createListMethod(creator, queryParameters);
        }
    }

    private void generateRestDataAdapter(ClassOutput output, String adapterName, String entityName) {
        try (ClassCreator creator = ClassCreator.builder()
                .classOutput(output)
                .className(adapterName)
                .superClass("dev.queryable.snello.runtime.SnelloPanacheAdapter")
                .build()) {
            creator.addAnnotation(jakarta.enterprise.context.ApplicationScoped.class);
            MethodCreator constructor = creator.getMethodCreator("<init>", void.class);
            constructor.invokeSpecialMethod(
                    MethodDescriptor.ofConstructor(
                            "dev.queryable.snello.runtime.SnelloPanacheAdapter", Class.class),
                    constructor.getThis(), constructor.loadClass(entityName));
            constructor.returnVoid();
        }
    }

    private static final String PARAMETER_ANNOTATION = "org.eclipse.microprofile.openapi.annotations.parameters.Parameter";

    private void createListMethod(ClassCreator creator, Map<String, String> queryParameters) {
        List<Class<?>> parameterTypes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> defaults = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        addParameter(parameterTypes, names, defaults, descriptions, "_start", String.class, "0",
                "Zero-based offset of the first record to return.");
        addParameter(parameterTypes, names, defaults, descriptions, "_limit", String.class, null,
                "Maximum number of records to return. 0 means unlimited.");
        addParameter(parameterTypes, names, defaults, descriptions, "_sort", String.class, null,
                "Sort specification: 'field:asc' or 'field:desc', comma-separated.");
        addParameter(parameterTypes, names, defaults, descriptions, "select_fields", String.class, null,
                "Comma-separated list of fields to include in the response.");
        for (Map.Entry<String, String> queryParameter : queryParameters.entrySet()) {
            addParameter(parameterTypes, names, defaults, descriptions, queryParameter.getKey(), String.class, null,
                    queryParameter.getValue());
        }
        parameterTypes.add(UriInfo.class);
        names.add("uriInfo");
        defaults.add(null);
        descriptions.add(null);

        MethodCreator method = creator.getMethodCreator(
                "list", Response.class, parameterTypes.toArray(Class<?>[]::new));
        method.addAnnotation(GET.class);
        for (int index = 0; index < names.size(); index++) {
            if ("uriInfo".equals(names.get(index))) {
                method.getParameterAnnotations(index).addAnnotation(Context.class);
                continue;
            }
            method.getParameterAnnotations(index).addAnnotation(QueryParam.class)
                    .addValue("value", names.get(index));
            if (defaults.get(index) != null) {
                method.getParameterAnnotations(index).addAnnotation(DefaultValue.class)
                        .addValue("value", defaults.get(index));
            }
            if (descriptions.get(index) != null) {
                method.getParameterAnnotations(index).addAnnotation(PARAMETER_ANNOTATION)
                        .addValue("description", descriptions.get(index));
            }
        }
        method.returnValue(method.invokeSpecialMethod(
                MethodDescriptor.ofMethod(
                        "dev.queryable.snello.runtime.SnelloGeneratedResource",
                        "listInternal", Response.class, UriInfo.class),
                method.getThis(), method.getMethodParam(names.size() - 1)));
    }

    /**
     * Query parameters declared on the generated list method for OpenAPI
     * documentation, mapped to their description: each scalar persistent field is
     * expanded with the filter suffixes that make sense for its type. Every
     * parameter is also parsed dynamically from the request.
     */
    private Map<String, String> queryParameters(ClassInfo entity, IndexView index) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ClassInfo current = entity; current != null; current = index.getClassByName(current.superName())) {
            for (FieldInfo field : current.fields()) {
                if (filterable(field, index)) {
                    for (String suffix : suffixes(field, index)) {
                        result.putIfAbsent(field.name() + suffix, describe(field.name(), suffix));
                    }
                }
            }
            if (current.superName() == null) {
                break;
            }
        }
        return result;
    }

    private String describe(String field, String suffix) {
        return switch (suffix) {
            case "" -> "Equality filter on '" + field + "'.";
            case "_ne" -> "Not-equal filter on '" + field + "'.";
            case "_lt" -> "Less-than filter on '" + field + "'.";
            case "_lte" -> "Less-than-or-equal filter on '" + field + "'.";
            case "_gt" -> "Greater-than filter on '" + field + "'.";
            case "_gte" -> "Greater-than-or-equal filter on '" + field + "'.";
            case "_contains" -> "Case-sensitive contains filter on '" + field + "'.";
            case "_like" -> "Case-sensitive contains filter on '" + field + "' (alias of _contains).";
            case "_ilike" -> "Case-insensitive contains filter on '" + field + "'.";
            case "_containss" -> "Case-insensitive contains filter on '" + field + "' (lowercases both sides).";
            case "_rlike" -> "Case-insensitive starts-with filter on '" + field + "'.";
            case "_llike" -> "Case-insensitive ends-with filter on '" + field + "'.";
            case "_ncontains" -> "Case-sensitive not-contains filter on '" + field + "'.";
            case "_in" -> "Comma-separated IN list filter on '" + field + "'.";
            case "_nn" -> "IS NOT NULL check on '" + field + "' (no value required).";
            case "_inn" -> "IS NULL check on '" + field + "' (no value required).";
            case "_nie" -> "Not-empty-string check on '" + field + "' (no value required).";
            case "_ie" -> "Empty-string check on '" + field + "' (no value required).";
            default -> "Filter on '" + field + "'.";
        };
    }

    private List<String> suffixes(FieldInfo field, IndexView index) {
        String typeName = field.type().name().toString();
        List<String> result = new ArrayList<>(List.of("", "_ne", "_in", "_nn", "_inn"));
        if ("java.lang.String".equals(typeName)) {
            result.addAll(List.of("_contains", "_like", "_ilike", "_containss", "_rlike", "_llike",
                    "_ncontains", "_nie", "_ie"));
            return result;
        }
        ClassInfo typeInfo = index.getClassByName(field.type().name());
        boolean enumType = typeInfo != null && (typeInfo.flags() & 0x4000) != 0;
        boolean rangeType = !enumType
                && !Set.of("boolean", "java.lang.Boolean", "java.util.UUID").contains(typeName);
        if (rangeType) {
            result.addAll(List.of("_lt", "_lte", "_gt", "_gte"));
        }
        return result;
    }

    private boolean filterable(FieldInfo field, IndexView index) {
        if (Modifier.isStatic(field.flags()) || Modifier.isTransient(field.flags())
                || field.hasAnnotation(TRANSIENT) || field.name().startsWith("$")) {
            return false;
        }
        for (DotName annotation : NON_FILTERABLE) {
            if (field.hasAnnotation(annotation)) {
                return false;
            }
        }
        Type type = field.type();
        if (type.kind() != Type.Kind.CLASS && type.kind() != Type.Kind.PRIMITIVE) {
            return false;
        }
        ClassInfo typeInfo = index.getClassByName(type.name());
        boolean enumType = typeInfo != null && (typeInfo.flags() & 0x4000) != 0;
        return SnelloTypes.isScalar(type.name().toString(), enumType);
    }

    private static void addParameter(List<Class<?>> types, List<String> names, List<String> defaults,
            List<String> descriptions, String name, Class<?> type, String defaultValue, String description) {
        types.add(type);
        names.add(name);
        defaults.add(defaultValue);
        descriptions.add(description);
    }
}
