CREATE TABLE club.clubs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE club.teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    club_id UUID NOT NULL REFERENCES club.clubs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE club.players (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    position VARCHAR(50) NOT NULL,
    team_id UUID NOT NULL REFERENCES club.teams(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
