CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE SCHEMA muse;

CREATE TABLE muse.user
(
    id         VARCHAR(30) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT current_timestamp
);

CREATE TABLE muse.user_session
(
    session_id    VARCHAR(1000) PRIMARY KEY NOT NULL,
    refresh_token VARCHAR(1000)             NOT NULL,
    user_id       VARCHAR(30)               NOT NULL,
    created_at    TIMESTAMP                 NOT NULL DEFAULT current_timestamp,

    CONSTRAINT userID
        FOREIGN KEY (user_id)
            REFERENCES muse.user (id) ON DELETE CASCADE
);

CREATE TABLE muse.review
(
    id          UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    created_at  TIMESTAMP   NOT NULL DEFAULT current_timestamp,
    creator_id  VARCHAR(30) NOT NULL,
    review_name VARCHAR(50) NOT NULL,
    is_public   BOOLEAN     NOT NULL,

    CONSTRAINT creatorID
        FOREIGN KEY (creator_id)
            REFERENCES muse.user (id)
);

-- TODO: should you be able to have multiple review entities?
CREATE TABLE muse.review_entity
(
    review_id   UUID        UNIQUE NOT NULL,
    --ENUM: Album, Artist, Playlist, Track
    entity_type INT         NOT NULL,
    entity_id   VARCHAR(50) NOT NULL,

    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (id) ON DELETE CASCADE
);

CREATE TABLE muse.review_link
(
    parent_review_id UUID NOT NULL,
    child_review_id  UUID NOT NULL,

    CONSTRAINT reviewLinkPrimaryKey
        PRIMARY KEY (parent_review_id, child_review_id),
    CONSTRAINT parentReview
        FOREIGN KEY (parent_review_id)
            REFERENCES muse.review (id),
    CONSTRAINT childReview
        FOREIGN KEY (child_review_id)
            REFERENCES muse.review (id)
);

CREATE TABLE muse.review_access
(
    review_id    UUID        NOT NULL,
    user_id      VARCHAR(30) NOT NULL,
    access_level INT         NOT NULL,

    CONSTRAINT reviewAccessPrimaryKey
        PRIMARY KEY (user_id, review_id),
    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (user_id)
            REFERENCES muse.user (id) ON DELETE CASCADE
);

CREATE TABLE muse.review_comment
(
    id                INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    created_at        TIMESTAMP DEFAULT current_timestamp,
    updated_at        TIMESTAMP DEFAULT current_timestamp,
    deleted           BOOLEAN   DEFAULT false,

    -- If null then root comment
    parent_comment_id INT         NULL,
    review_id         UUID        NOT NULL,
    commenter         VARCHAR(30) NOT NULL,
    -- Comment can be null if deleted
    comment           VARCHAR(10000),

    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (commenter)
            REFERENCES muse.user (id),
    CONSTRAINT parentCommentID
        FOREIGN KEY (parent_comment_id)
            REFERENCES muse.review_comment (id)
);

CREATE TABLE muse.review_comment_entity
(
    comment_id  INT         NOT NULL,
    --ENUM: Album, Artist, Playlist, Track
    entity_type INT         NOT NULL,
    entity_id   VARCHAR(50) NOT NULL,

    CONSTRAINT commentID
        FOREIGN KEY (comment_id)
            REFERENCES muse.review_comment (id) ON DELETE CASCADE,
    CONSTRAINT reviewCommentEntityPrimaryKey
        PRIMARY KEY (comment_id, entity_type, entity_id)
);