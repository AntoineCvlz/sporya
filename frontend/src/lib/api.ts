// Chemin relatif — même valeur en local (proxy Vite, voir vite.config.ts) et
// en prod (routage Ingress, voir infrastructure/kubernetes/ingress/). Pas de
// configuration d'URL de base ni de CORS à gérer.

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string }
    return body.message ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status)
  }
  return (await response.json()) as T
}

function authHeaders(accessToken: string): HeadersInit {
  return { Authorization: `Bearer ${accessToken}` }
}

export interface UserResponse {
  id: string
  email: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export function register(email: string, password: string): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function me(accessToken: string): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/me', { headers: authHeaders(accessToken) })
}

export interface ClubResponse {
  id: string
  name: string
  country: string
  createdBy: string
  createdAt: string
}

export interface TeamResponse {
  id: string
  name: string
  clubId: string
  createdAt: string
}

export interface PlayerResponse {
  id: string
  name: string
  birthdate: string
  position: string
  teamId: string
  createdAt: string
}

export function listClubs(accessToken: string): Promise<ClubResponse[]> {
  return request<ClubResponse[]>('/api/v1/clubs', { headers: authHeaders(accessToken) })
}

export function createClub(
  accessToken: string,
  name: string,
  country: string,
): Promise<ClubResponse> {
  return request<ClubResponse>('/api/v1/clubs', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name, country }),
  })
}

export function getClub(accessToken: string, clubId: string): Promise<ClubResponse> {
  return request<ClubResponse>(`/api/v1/clubs/${clubId}`, { headers: authHeaders(accessToken) })
}

export function listTeams(accessToken: string, clubId: string): Promise<TeamResponse[]> {
  return request<TeamResponse[]>(`/api/v1/clubs/${clubId}/teams`, {
    headers: authHeaders(accessToken),
  })
}

export function createTeam(
  accessToken: string,
  clubId: string,
  name: string,
): Promise<TeamResponse> {
  return request<TeamResponse>(`/api/v1/clubs/${clubId}/teams`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name }),
  })
}

export function getTeam(accessToken: string, teamId: string): Promise<TeamResponse> {
  return request<TeamResponse>(`/api/v1/teams/${teamId}`, { headers: authHeaders(accessToken) })
}

export function listPlayers(accessToken: string, teamId: string): Promise<PlayerResponse[]> {
  return request<PlayerResponse[]>(`/api/v1/teams/${teamId}/players`, {
    headers: authHeaders(accessToken),
  })
}

export function createPlayer(
  accessToken: string,
  teamId: string,
  name: string,
  birthdate: string,
  position: string,
): Promise<PlayerResponse> {
  return request<PlayerResponse>(`/api/v1/teams/${teamId}/players`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name, birthdate, position }),
  })
}

export interface MemberResponse {
  userId: string
  email: string
  role: string
  createdAt: string
}

export function listMembers(accessToken: string, clubId: string): Promise<MemberResponse[]> {
  return request<MemberResponse[]>(`/api/v1/clubs/${clubId}/members`, {
    headers: authHeaders(accessToken),
  })
}

export function addMember(
  accessToken: string,
  clubId: string,
  email: string,
  role: string,
): Promise<MemberResponse> {
  return request<MemberResponse>(`/api/v1/clubs/${clubId}/members`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ email, role }),
  })
}

export interface CompetitionResponse {
  id: string
  name: string
  createdAt: string
}

export interface SeasonResponse {
  id: string
  label: string
  competitionId: string
  createdAt: string
}

export interface MatchResponse {
  id: string
  seasonId: string
  homeTeamId: string
  awayTeamId: string
  status: string
  kickoffAt: string
  homeScore: number
  awayScore: number
  createdAt: string
}

export interface MatchEventResponse {
  id: string
  matchId: string
  type: string
  minute: number
  playerId: string
  teamId: string
  createdAt: string
}

export function listCompetitions(accessToken: string): Promise<CompetitionResponse[]> {
  return request<CompetitionResponse[]>('/api/v1/competitions', { headers: authHeaders(accessToken) })
}

export function createCompetition(accessToken: string, name: string): Promise<CompetitionResponse> {
  return request<CompetitionResponse>('/api/v1/competitions', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ name }),
  })
}

export function listSeasons(accessToken: string, competitionId: string): Promise<SeasonResponse[]> {
  return request<SeasonResponse[]>(`/api/v1/competitions/${competitionId}/seasons`, {
    headers: authHeaders(accessToken),
  })
}

export function createSeason(
  accessToken: string,
  competitionId: string,
  label: string,
): Promise<SeasonResponse> {
  return request<SeasonResponse>(`/api/v1/competitions/${competitionId}/seasons`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ label }),
  })
}

export function listMatches(accessToken: string): Promise<MatchResponse[]> {
  return request<MatchResponse[]>('/api/v1/matches', { headers: authHeaders(accessToken) })
}

export function getMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return request<MatchResponse>(`/api/v1/matches/${matchId}`, { headers: authHeaders(accessToken) })
}

export function createMatch(
  accessToken: string,
  seasonId: string,
  homeTeamId: string,
  awayTeamId: string,
  kickoffAt: string,
): Promise<MatchResponse> {
  return request<MatchResponse>('/api/v1/matches', {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ seasonId, homeTeamId, awayTeamId, kickoffAt }),
  })
}

function transitionMatch(accessToken: string, matchId: string, transition: string): Promise<MatchResponse> {
  return request<MatchResponse>(`/api/v1/matches/${matchId}/${transition}`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  })
}

export function startMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'start')
}

export function halfTimeMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'half-time')
}

export function resumeMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'resume')
}

export function finishMatch(accessToken: string, matchId: string): Promise<MatchResponse> {
  return transitionMatch(accessToken, matchId, 'finish')
}

export function listMatchEvents(accessToken: string, matchId: string): Promise<MatchEventResponse[]> {
  return request<MatchEventResponse[]>(`/api/v1/matches/${matchId}/events`, {
    headers: authHeaders(accessToken),
  })
}

export function addMatchEvent(
  accessToken: string,
  matchId: string,
  type: string,
  minute: number,
  playerId: string,
): Promise<MatchEventResponse> {
  return request<MatchEventResponse>(`/api/v1/matches/${matchId}/events`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ type, minute, playerId }),
  })
}

export interface PlayerStatsResponse {
  playerId: string
  goals: number
  yellowCards: number
  redCards: number
  matchesPlayed: number
}

export interface RecentMatchResult {
  matchId: string
  result: string
  opponentTeamId: string
  homeScore: number
  awayScore: number
  kickoffAt: string
}

export interface TeamFormResponse {
  teamId: string
  recentResults: RecentMatchResult[]
}

export function getPlayerStats(accessToken: string, playerId: string): Promise<PlayerStatsResponse> {
  return request<PlayerStatsResponse>(`/api/v1/players/${playerId}/stats`, {
    headers: authHeaders(accessToken),
  })
}

export function getTeamForm(accessToken: string, teamId: string): Promise<TeamFormResponse> {
  return request<TeamFormResponse>(`/api/v1/teams/${teamId}/form`, {
    headers: authHeaders(accessToken),
  })
}
