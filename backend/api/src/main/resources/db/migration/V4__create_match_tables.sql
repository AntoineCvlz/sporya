CREATE SCHEMA IF NOT EXISTS match;

CREATE TABLE match.competitions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.seasons (
    id UUID PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    competition_id UUID NOT NULL REFERENCES match.competitions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.matches (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES match.seasons(id),
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    kickoff_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match.match_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES match.matches(id),
    type VARCHAR(20) NOT NULL,
    minute INT NOT NULL,
    player_id UUID NOT NULL,
    team_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
