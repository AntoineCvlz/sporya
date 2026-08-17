import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  addMatchEvent,
  finishMatch,
  getMatch,
  halfTimeMatch,
  listMatchEvents,
  listPlayers,
  resumeMatch,
  startMatch,
} from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const EVENT_TYPES = ['GOAL_SCORED', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION'] as const

const selectClassName =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'

export function MatchDetailPage() {
  const { matchId } = useParams<{ matchId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [eventType, setEventType] = useState<string>(EVENT_TYPES[0])
  const [minute, setMinute] = useState('')
  const [playerId, setPlayerId] = useState('')

  const matchQuery = useQuery({
    queryKey: ['match', matchId],
    queryFn: () => getMatch(accessToken as string, matchId as string),
    enabled: Boolean(accessToken && matchId),
  })

  const eventsQuery = useQuery({
    queryKey: ['match-events', matchId],
    queryFn: () => listMatchEvents(accessToken as string, matchId as string),
    enabled: Boolean(accessToken && matchId),
  })

  const homePlayersQuery = useQuery({
    queryKey: ['players', matchQuery.data?.homeTeamId],
    queryFn: () => listPlayers(accessToken as string, matchQuery.data!.homeTeamId),
    enabled: Boolean(accessToken && matchQuery.data),
  })

  const awayPlayersQuery = useQuery({
    queryKey: ['players', matchQuery.data?.awayTeamId],
    queryFn: () => listPlayers(accessToken as string, matchQuery.data!.awayTeamId),
    enabled: Boolean(accessToken && matchQuery.data),
  })

  const roster = [...(homePlayersQuery.data ?? []), ...(awayPlayersQuery.data ?? [])]

  function invalidateMatch() {
    queryClient.invalidateQueries({ queryKey: ['match', matchId] })
    queryClient.invalidateQueries({ queryKey: ['match-events', matchId] })
  }

  const startMutation = useMutation({
    mutationFn: () => startMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const halfTimeMutation = useMutation({
    mutationFn: () => halfTimeMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const resumeMutation = useMutation({
    mutationFn: () => resumeMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })
  const finishMutation = useMutation({
    mutationFn: () => finishMatch(accessToken as string, matchId as string),
    onSuccess: invalidateMatch,
  })

  const addEventMutation = useMutation({
    mutationFn: () =>
      addMatchEvent(accessToken as string, matchId as string, eventType, Number(minute), playerId),
    onSuccess: () => {
      setMinute('')
      setPlayerId('')
      invalidateMatch()
    },
  })

  function handleAddEvent(event: FormEvent) {
    event.preventDefault()
    addEventMutation.mutate()
  }

  const status = matchQuery.data?.status

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/matches" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour aux matchs
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>
            {matchQuery.data
              ? `${matchQuery.data.homeScore} : ${matchQuery.data.awayScore}`
              : 'Match'}
          </CardTitle>
          <CardDescription>{status}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {status === 'SCHEDULED' && (
            <Button onClick={() => startMutation.mutate()} disabled={startMutation.isPending}>
              Démarrer
            </Button>
          )}
          {status === 'LIVE' && (
            <>
              <Button onClick={() => halfTimeMutation.mutate()} disabled={halfTimeMutation.isPending}>
                Mi-temps
              </Button>
              <Button onClick={() => finishMutation.mutate()} disabled={finishMutation.isPending}>
                Terminer
              </Button>
            </>
          )}
          {status === 'HALF_TIME' && (
            <Button onClick={() => resumeMutation.mutate()} disabled={resumeMutation.isPending}>
              Reprendre
            </Button>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Événements</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {eventsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun événement pour l'instant.</p>
          )}
          {eventsQuery.data?.map((event) => (
            <div key={event.id} className="rounded-md border border-border px-3 py-2 text-sm">
              {event.minute}&apos; — {event.type}
            </div>
          ))}
        </CardContent>
      </Card>

      {status === 'LIVE' && (
        <Card>
          <CardHeader>
            <CardTitle>Ajouter un événement</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleAddEvent} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-type">Type</Label>
                <select
                  id="event-type"
                  className={selectClassName}
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                >
                  {EVENT_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-minute">Minute</Label>
                <Input
                  id="event-minute"
                  type="number"
                  min={0}
                  required
                  value={minute}
                  onChange={(e) => setMinute(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="event-player">Joueur</Label>
                <select
                  id="event-player"
                  className={selectClassName}
                  required
                  value={playerId}
                  onChange={(e) => setPlayerId(e.target.value)}
                >
                  <option value="">Choisir…</option>
                  {roster.map((player) => (
                    <option key={player.id} value={player.id}>
                      {player.name}
                    </option>
                  ))}
                </select>
              </div>
              <Button type="submit" disabled={addEventMutation.isPending}>
                {addEventMutation.isPending ? 'Ajout…' : 'Ajouter'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
