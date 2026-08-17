import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createCompetition, createSeason, listCompetitions, listSeasons } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function CompetitionsPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [seasonLabels, setSeasonLabels] = useState<Record<string, string>>({})

  const competitionsQuery = useQuery({
    queryKey: ['competitions', accessToken],
    queryFn: () => listCompetitions(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const createCompetitionMutation = useMutation({
    mutationFn: () => createCompetition(accessToken as string, name),
    onSuccess: () => {
      setName('')
      queryClient.invalidateQueries({ queryKey: ['competitions'] })
    },
  })

  const createSeasonMutation = useMutation({
    mutationFn: (competitionId: string) =>
      createSeason(accessToken as string, competitionId, seasonLabels[competitionId] ?? ''),
    onSuccess: (_data, competitionId) => {
      setSeasonLabels((labels) => ({ ...labels, [competitionId]: '' }))
      queryClient.invalidateQueries({ queryKey: ['seasons', competitionId] })
    },
  })

  function handleCreateCompetition(event: FormEvent) {
    event.preventDefault()
    createCompetitionMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/dashboard" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour au tableau de bord
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>Compétitions</CardTitle>
          <CardDescription>Liste des compétitions et de leurs saisons.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {competitionsQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Chargement…</p>
          )}
          {competitionsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucune compétition pour l'instant.</p>
          )}
          {competitionsQuery.data?.map((competition) => (
            <CompetitionRow
              key={competition.id}
              competitionId={competition.id}
              competitionName={competition.name}
              accessToken={accessToken as string}
              seasonLabel={seasonLabels[competition.id] ?? ''}
              onSeasonLabelChange={(label) =>
                setSeasonLabels((labels) => ({ ...labels, [competition.id]: label }))
              }
              onCreateSeason={() => createSeasonMutation.mutate(competition.id)}
              creatingSeason={createSeasonMutation.isPending}
            />
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer une compétition</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreateCompetition} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="competition-name">Nom</Label>
              <Input
                id="competition-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createCompetitionMutation.isPending}>
              {createCompetitionMutation.isPending ? 'Création…' : 'Créer'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

function CompetitionRow({
  competitionId,
  competitionName,
  accessToken,
  seasonLabel,
  onSeasonLabelChange,
  onCreateSeason,
  creatingSeason,
}: {
  competitionId: string
  competitionName: string
  accessToken: string
  seasonLabel: string
  onSeasonLabelChange: (label: string) => void
  onCreateSeason: () => void
  creatingSeason: boolean
}) {
  const seasonsQuery = useQuery({
    queryKey: ['seasons', competitionId],
    queryFn: () => listSeasons(accessToken, competitionId),
    enabled: Boolean(accessToken),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onCreateSeason()
  }

  return (
    <div className="rounded-md border border-border p-3">
      <p className="font-medium">{competitionName}</p>
      <div className="mt-2 flex flex-col gap-1">
        {seasonsQuery.data?.map((season) => (
          <p key={season.id} className="text-sm text-muted-foreground">
            {season.label}
          </p>
        ))}
      </div>
      <form onSubmit={handleSubmit} className="mt-2 flex gap-2">
        <Input
          placeholder="Saison (ex: 2026)"
          value={seasonLabel}
          onChange={(e) => onSeasonLabelChange(e.target.value)}
          required
        />
        <Button type="submit" size="sm" disabled={creatingSeason}>
          Ajouter
        </Button>
      </form>
    </div>
  )
}
