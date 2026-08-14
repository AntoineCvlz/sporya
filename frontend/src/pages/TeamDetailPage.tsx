import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createPlayer, getTeam, listPlayers } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [birthdate, setBirthdate] = useState('')
  const [position, setPosition] = useState('')

  const teamQuery = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => getTeam(accessToken as string, teamId as string),
    enabled: Boolean(accessToken && teamId),
  })

  const playersQuery = useQuery({
    queryKey: ['players', teamId],
    queryFn: () => listPlayers(accessToken as string, teamId as string),
    enabled: Boolean(accessToken && teamId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createPlayer(accessToken as string, teamId as string, name, birthdate, position),
    onSuccess: () => {
      setName('')
      setBirthdate('')
      setPosition('')
      queryClient.invalidateQueries({ queryKey: ['players', teamId] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link
        to={teamQuery.data ? `/clubs/${teamQuery.data.clubId}` : '/clubs'}
        className="text-sm text-muted-foreground underline underline-offset-4"
      >
        ← Retour au club
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{teamQuery.data?.name ?? 'Équipe'}</CardTitle>
          <CardDescription>Effectif</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {playersQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {playersQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun joueur pour l'instant.</p>
          )}
          {playersQuery.data?.map((player) => (
            <div key={player.id} className="rounded-md border border-border px-3 py-2 text-sm">
              {player.name} — {player.position} ({player.birthdate})
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Ajouter un joueur</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-name">Nom</Label>
              <Input
                id="player-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-birthdate">Date de naissance</Label>
              <Input
                id="player-birthdate"
                type="date"
                required
                value={birthdate}
                onChange={(e) => setBirthdate(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="player-position">Poste</Label>
              <Input
                id="player-position"
                required
                value={position}
                onChange={(e) => setPosition(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Ajout…' : 'Ajouter'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
