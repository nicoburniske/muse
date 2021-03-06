CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE SCHEMA muse;

CREATE TABLE muse.app_user
(
    id         VARCHAR(30) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT current_timestamp
);

CREATE TABLE muse.review
(
    id          UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    created_at  TIMESTAMP   NOT NULL DEFAULT current_timestamp,
    creator_id  VARCHAR(30) NOT NULL,
    review_name VARCHAR(50) NOT NULL,
    is_public   BOOLEAN     NOT NULL,
    --ENUM: Album, Artist, Playlist, Track
    entity_type INT         NOT NULL,
    entity_id   VARCHAR(50) NOT NULL,

    CONSTRAINT creatorID
        FOREIGN KEY (creator_id)
            REFERENCES muse.app_user (id)
);

CREATE TABLE muse.review_access
(
    review_id     UUID        NOT NULL,
    user_id       VARCHAR(30) NOT NULL,
    review_access INT         NOT NULL,
    CONSTRAINT reviewID
        FOREIGN KEY (review_id) REFERENCES muse.review (id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (user_id) REFERENCES muse.app_user (id) ON DELETE CASCADE,
    CONSTRAINT pk PRIMARY KEY (user_id, review_id)
);


CREATE TABLE muse.review_comment
(
    id                INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    created_at        TIMESTAMP DEFAULT current_timestamp,
    updated_at        TIMESTAMP DEFAULT current_timestamp,
    parent_comment_id INT         NOT NULL,
    review_id         UUID        NOT NULL,
    commenter         VARCHAR(30) NOT NULL,
    comment           VARCHAR(1000),
    rating            INT,

    --ENUM: Album, Artist, Playlist, Track
    entity_type       INT         NOT NULL,
    entity_id         VARCHAR(50) NOT NULL,


    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (commenter)
            REFERENCES muse.app_user (id),
    CONSTRAINT parentCommentID
        FOREIGN KEY (parent_comment_id)
            REFERENCES muse.review_comment (id),
    CONSTRAINT checkContent check (
        comment IS NOT NULL OR rating IS NOT NULL
        )
);