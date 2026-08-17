CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    match_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    home_score INT NOT NULL,
    away_score INT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
