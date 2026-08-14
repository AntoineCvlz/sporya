import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { me } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function DashboardPage() {
  const navigate = useNavigate()
  const { accessToken, setAccessToken } = useAuth()

  const query = useQuery({
    queryKey: ['me', accessToken],
    queryFn: () => me(accessToken as string),
    enabled: Boolean(accessToken),
  })

  function handleLogout() {
    setAccessToken(null)
    navigate('/login')
  }

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Tableau de bord</CardTitle>
          <CardDescription>Squelette minimal — le vrai dashboard arrive avec Club/Match Service.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {query.isLoading && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {query.isError && (
            <p className="text-sm text-destructive">Impossible de charger le profil.</p>
          )}
          {query.data && (
            <div className="text-sm">
              <p className="text-muted-foreground">Connecté en tant que</p>
              <p className="font-medium">{query.data.email}</p>
            </div>
          )}
          <Button asChild variant="secondary">
            <Link to="/clubs">Voir les clubs</Link>
          </Button>
          <Button variant="outline" onClick={handleLogout}>
            Se déconnecter
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
