CREATE SCHEMA muse;

CREATE TABLE muse.user
(
    user_id    VARCHAR(30) PRIMARY KEY,
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
            REFERENCES muse.user (user_id) ON DELETE CASCADE
);

CREATE TABLE muse.review
(
    review_id   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    created_at  TIMESTAMP   NOT NULL DEFAULT current_timestamp,
    creator_id  VARCHAR(30) NOT NULL,
    review_name VARCHAR(50) NOT NULL,
    is_public   BOOLEAN     NOT NULL,

    CONSTRAINT creatorID
        FOREIGN KEY (creator_id)
            REFERENCES muse.user (user_id)
);

CREATE TABLE muse.review_entity
(
    review_id   UUID UNIQUE NOT NULL,
    --ENUM: Album, Artist, Playlist, Track
    entity_type INT         NOT NULL,
    entity_id   VARCHAR(50) NOT NULL,

    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (review_id) ON DELETE CASCADE
);

CREATE TABLE muse.review_link
(
    link_id          INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    link_index       INT  NOT NULL,
    parent_review_id UUID NOT NULL,
    child_review_id  UUID NOT NULL,

    CONSTRAINT parentReview
        FOREIGN KEY (parent_review_id)
            REFERENCES muse.review (review_id) ON DELETE CASCADE,
    CONSTRAINT childReview
        FOREIGN KEY (child_review_id)
            REFERENCES muse.review (review_id) ON DELETE CASCADE,
    -- Ensure index integrity.
    CONSTRAINT nonNegativeIndex CHECK (link_index >= 0),
    CONSTRAINT reviewLinkUnique
        UNIQUE (parent_review_id, child_review_id),
    CONSTRAINT uniqueLinkIndex
        UNIQUE (parent_review_id, link_index)
            DEFERRABLE INITIALLY DEFERRED
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
            REFERENCES muse.review (review_id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (user_id)
            REFERENCES muse.user (user_id) ON DELETE CASCADE
);

CREATE TABLE muse.review_comment
(
    comment_id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    created_at TIMESTAMP DEFAULT current_timestamp,
    updated_at TIMESTAMP DEFAULT current_timestamp,
    deleted    BOOLEAN   DEFAULT false,
    review_id  UUID        NOT NULL,
    commenter  VARCHAR(30) NOT NULL,
    -- Comment can be null if deleted
    comment    VARCHAR(10000),

    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (review_id) ON DELETE CASCADE,
    CONSTRAINT userID
        FOREIGN KEY (commenter)
            REFERENCES muse.user (user_id)
);


CREATE TABLE muse.review_comment_index
(
    review_id         UUID   NOT NULL,
    comment_id        BIGINT NOT NULL,

    -- Comments are ordered by level.
    comment_index     INT    NOT NULL,
    parent_comment_id BIGINT,

    PRIMARY KEY (review_id, comment_id),
    CONSTRAINT commentID
        FOREIGN KEY (comment_id)
            REFERENCES muse.review_comment (comment_id) ON DELETE CASCADE,
    CONSTRAINT parentCommentID
        FOREIGN KEY (parent_comment_id)
            REFERENCES muse.review_comment (comment_id) ON DELETE CASCADE,
    CONSTRAINT reviewID
        FOREIGN KEY (review_id)
            REFERENCES muse.review (review_id) ON DELETE CASCADE,
    -- Ensure index integrity.
    CONSTRAINT nonNegativeIndex CHECK (comment_index >= 0),
    CONSTRAINT uniqueCommentIndexChild
        EXCLUDE (
        parent_comment_id WITH =,
        comment_index WITH =
        ) WHERE (parent_comment_id IS NOT NULL) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uniqueCommentIndexParent
        EXCLUDE (
        comment_index WITH =,
        review_id WITH =
        ) WHERE (parent_comment_id IS NULL) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE muse.review_comment_parent_child
(
    parent_comment_id BIGINT NOT NULL,
    child_comment_id  BIGINT NOT NULL,

    CONSTRAINT parentChildCommentPrimaryKey
        PRIMARY KEY (parent_comment_id, child_comment_id),
    CONSTRAINT parentCommentID
        FOREIGN KEY (parent_comment_id)
            REFERENCES muse.review_comment (comment_id) ON DELETE CASCADE,
    CONSTRAINT childCommentID
        FOREIGN KEY (child_comment_id)
            REFERENCES muse.review_comment (comment_id) ON DELETE CASCADE
);

CREATE TABLE muse.review_comment_entity
(
    comment_id  BIGINT      NOT NULL,
    --ENUM: Album, Artist, Playlist, Track
    entity_type INT         NOT NULL,
    entity_id   VARCHAR(50) NOT NULL,

    CONSTRAINT reviewCommentEntityPrimaryKey
        PRIMARY KEY (comment_id, entity_type, entity_id),
    CONSTRAINT commentID
        FOREIGN KEY (comment_id)
            REFERENCES muse.review_comment (comment_id) ON DELETE CASCADE
);