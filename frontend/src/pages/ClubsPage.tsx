import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createClub, listClubs } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function ClubsPage() {
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [country, setCountry] = useState('')

  const clubsQuery = useQuery({
    queryKey: ['clubs', accessToken],
    queryFn: () => listClubs(accessToken as string),
    enabled: Boolean(accessToken),
  })

  const createMutation = useMutation({
    mutationFn: () => createClub(accessToken as string, name, country),
    onSuccess: () => {
      setName('')
      setCountry('')
      queryClient.invalidateQueries({ queryKey: ['clubs'] })
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
          <CardTitle>Clubs</CardTitle>
          <CardDescription>Liste des clubs existants.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {clubsQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {clubsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun club pour l'instant.</p>
          )}
          {clubsQuery.data?.map((club) => (
            <Link
              key={club.id}
              to={`/clubs/${club.id}`}
              className="rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              {club.name} — {club.country}
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer un club</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="name">Nom</Label>
              <Input id="name" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="country">Pays</Label>
              <Input
                id="country"
                required
                value={country}
                onChange={(e) => setCountry(e.target.value)}
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
