import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createMatch,
  listClubs,
  listCompetitions,
  listMatches,
  listSeasons,
  listTeams,
} from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const selectClassName =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'

export function MatchesPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()

  const [competitionId, setCompetitionId] = useState('')
  const [seasonId, setSeasonId] = useState('')
  const [homeClubId, setHomeClubId] = useState('')
  const [homeTeamId, setHomeTeamId] = useState('')
  const [awayClubId, setAwayClubId] = useState('')
  const [awayTeamId, setAwayTeamId] = useState('')
  const [kickoffAt, setKickoffAt] = useState('')

  const matchesQuery = useQuery({
    queryKey: ['matches', accessToken],
    queryFn: () => listMatches(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const competitionsQuery = useQuery({
    queryKey: ['competitions', accessToken],
    queryFn: () => listCompetitions(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const seasonsQuery = useQuery({
    queryKey: ['seasons', competitionId],
    queryFn: () => listSeasons(accessToken as string, competitionId),
    enabled: Boolean(accessToken && competitionId),
  })

  const clubsQuery = useQuery({
    queryKey: ['clubs', accessToken],
    queryFn: () => listClubs(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const homeTeamsQuery = useQuery({
    queryKey: ['teams', homeClubId],
    queryFn: () => listTeams(accessToken as string, homeClubId),
    enabled: Boolean(accessToken && homeClubId),
  })

  const awayTeamsQuery = useQuery({
    queryKey: ['teams', awayClubId],
    queryFn: () => listTeams(accessToken as string, awayClubId),
    enabled: Boolean(accessToken && awayClubId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createMatch(
        accessToken as string,
        seasonId,
        homeTeamId,
        awayTeamId,
        new Date(kickoffAt).toISOString(),
      ),
    onSuccess: () => {
      setKickoffAt('')
      queryClient.invalidateQueries({ queryKey: ['matches'] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/dashboard" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour au tableau de bord
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>Matchs</CardTitle>
          <CardDescription>Liste des matchs planifiés et en cours.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {matchesQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {matchesQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun match pour l'instant.</p>
          )}
          {matchesQuery.data?.map((match) => (
            <Link
              key={match.id}
              to={`/matches/${match.id}`}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              <span>{new Date(match.kickoffAt).toLocaleString()}</span>
              <span className="text-muted-foreground">
                {match.status} — {match.homeScore} : {match.awayScore}
              </span>
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer un match</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="competition">Compétition</Label>
              <select
                id="competition"
                className={selectClassName}
                required
                value={competitionId}
                onChange={(e) => {
                  setCompetitionId(e.target.value)
                  setSeasonId('')
                }}
              >
                <option value="">Choisir…</option>
                {competitionsQuery.data?.map((competition) => (
                  <option key={competition.id} value={competition.id}>
                    {competition.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="season">Saison</Label>
              <select
                id="season"
                className={selectClassName}
                required
                value={seasonId}
                onChange={(e) => setSeasonId(e.target.value)}
                disabled={!competitionId}
              >
                <option value="">Choisir…</option>
                {seasonsQuery.data?.map((season) => (
                  <option key={season.id} value={season.id}>
                    {season.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="home-club">Club domicile</Label>
              <select
                id="home-club"
                className={selectClassName}
                required
                value={homeClubId}
                onChange={(e) => {
                  setHomeClubId(e.target.value)
                  setHomeTeamId('')
                }}
              >
                <option value="">Choisir…</option>
                {clubsQuery.data?.map((club) => (
                  <option key={club.id} value={club.id}>
                    {club.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="home-team">Équipe domicile</Label>
              <select
                id="home-team"
                className={selectClassName}
                required
                value={homeTeamId}
                onChange={(e) => setHomeTeamId(e.target.value)}
                disabled={!homeClubId}
              >
                <option value="">Choisir…</option>
                {homeTeamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="away-club">Club extérieur</Label>
              <select
                id="away-club"
                className={selectClassName}
                required
                value={awayClubId}
                onChange={(e) => {
                  setAwayClubId(e.target.value)
                  setAwayTeamId('')
                }}
              >
                <option value="">Choisir…</option>
                {clubsQuery.data?.map((club) => (
                  <option key={club.id} value={club.id}>
                    {club.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="away-team">Équipe extérieure</Label>
              <select
                id="away-team"
                className={selectClassName}
                required
                value={awayTeamId}
                onChange={(e) => setAwayTeamId(e.target.value)}
                disabled={!awayClubId}
              >
                <option value="">Choisir…</option>
                {awayTeamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="kickoff">Coup d'envoi</Label>
              <Input
                id="kickoff"
                type="datetime-local"
                required
                value={kickoffAt}
                onChange={(e) => setKickoffAt(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Création…' : 'Créer'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
