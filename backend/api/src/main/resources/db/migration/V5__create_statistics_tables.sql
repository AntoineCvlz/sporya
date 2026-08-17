CREATE SCHEMA IF NOT EXISTS statistics;

CREATE TABLE statistics.player_match_statistics (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    match_id UUID NOT NULL,
    team_id UUID NOT NULL,
    season_id UUID NOT NULL,
    goals INT NOT NULL,
    yellow_cards INT NOT NULL,
    red_cards INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, match_id)
);

CREATE TABLE statistics.team_match_statistics (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    match_id UUID NOT NULL,
    season_id UUID NOT NULL,
    goals_for INT NOT NULL,
    goals_against INT NOT NULL,
    result VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, match_id)
);
