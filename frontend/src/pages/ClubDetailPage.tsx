import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { addMember, createTeam, getClub, listMembers, listTeams } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const ROLES = ['ADMIN', 'COACH', 'ANALYST', 'PLAYER', 'VIEWER'] as const

export function ClubDetailPage() {
  const { clubId } = useParams<{ clubId: string }>()
  const { accessToken } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [memberEmail, setMemberEmail] = useState('')
  const [memberRole, setMemberRole] = useState<string>(ROLES[1])

  const clubQuery = useQuery({
    queryKey: ['club', clubId],
    queryFn: () => getClub(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const teamsQuery = useQuery({
    queryKey: ['teams', clubId],
    queryFn: () => listTeams(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const membersQuery = useQuery({
    queryKey: ['members', clubId],
    queryFn: () => listMembers(accessToken as string, clubId as string),
    enabled: Boolean(accessToken && clubId),
  })

  const createMutation = useMutation({
    mutationFn: () => createTeam(accessToken as string, clubId as string, name),
    onSuccess: () => {
      setName('')
      queryClient.invalidateQueries({ queryKey: ['teams', clubId] })
    },
  })

  const addMemberMutation = useMutation({
    mutationFn: () => addMember(accessToken as string, clubId as string, memberEmail, memberRole),
    onSuccess: () => {
      setMemberEmail('')
      queryClient.invalidateQueries({ queryKey: ['members', clubId] })
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate()
  }

  function handleAddMember(event: FormEvent) {
    event.preventDefault()
    addMemberMutation.mutate()
  }

  return (
    <div className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-6">
      <Link to="/clubs" className="text-sm text-muted-foreground underline underline-offset-4">
        ← Retour aux clubs
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{clubQuery.data?.name ?? 'Club'}</CardTitle>
          <CardDescription>{clubQuery.data?.country}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {teamsQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {teamsQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucune équipe pour l'instant.</p>
          )}
          {teamsQuery.data?.map((team) => (
            <Link
              key={team.id}
              to={`/teams/${team.id}`}
              className="rounded-md border border-border px-3 py-2 text-sm hover:bg-accent"
            >
              {team.name}
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Créer une équipe</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="team-name">Nom</Label>
              <Input
                id="team-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Création…' : 'Créer'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Membres</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {membersQuery.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {membersQuery.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">Aucun membre pour l'instant.</p>
          )}
          {membersQuery.data?.map((member) => (
            <div
              key={member.userId}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
            >
              <span>{member.email}</span>
              <span className="text-muted-foreground">{member.role}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Ajouter un membre</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleAddMember} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="member-email">Email</Label>
              <Input
                id="member-email"
                type="email"
                required
                value={memberEmail}
                onChange={(e) => setMemberEmail(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="member-role">Rôle</Label>
              <select
                id="member-role"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={memberRole}
                onChange={(e) => setMemberRole(e.target.value)}
              >
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" disabled={addMemberMutation.isPending}>
              {addMemberMutation.isPending ? 'Ajout…' : 'Ajouter'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
