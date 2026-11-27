INSERT INTO team(id, name) VALUES (1, 'backend');
INSERT INTO team(id, name) VALUES (2, 'frontend');

INSERT INTO articles(id, title, status, price, views, published_at, description, avatar, team_id) VALUES
 (1, 'Spring Boot in Action', 'published', 100.00, 150, '2024-01-10', 'A book about spring', 'a1.png', 1);
INSERT INTO articles(id, title, status, price, views, published_at, description, avatar, team_id) VALUES
 (2, 'Quarkus Unleashed', 'draft', 50.00, 10, '2024-03-01', '', NULL, 1);
INSERT INTO articles(id, title, status, price, views, published_at, description, avatar, team_id) VALUES
 (3, 'SKU Management', 'review', 200.00, 75, '2024-02-15', 'warehouse codes', 'a3.png', 2);
INSERT INTO articles(id, title, status, price, views, published_at, description, avatar, team_id) VALUES
 (4, 'Advanced SPRING patterns', 'published', 150.00, 300, '2023-12-01', NULL, NULL, 2);
INSERT INTO articles(id, title, status, price, views, published_at, description, avatar, team_id) VALUES
 (5, 'Debug like a pro', 'cancelled', 80.00, 5, NULL, 'debugging tips', 'a5.png', NULL);

ALTER SEQUENCE articles_seq RESTART WITH 1000;
ALTER SEQUENCE team_seq RESTART WITH 1000;
