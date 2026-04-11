export default function Home() {
  return (
    <div style={{ padding: '2rem' }}>
      <h1>Sarbi Rohek - API Backend</h1>
      <p>Plateforme de services - Hébergée sur Vercel</p>
      
      <h2>Endpoints disponibles:</h2>
      <ul>
        <li><strong>POST /api/auth/register</strong> - Inscription utilisateur</li>
        <li><strong>POST /api/auth/login</strong> - Connexion utilisateur</li>
        <li><strong>GET /api/health</strong> - Vérifier l'état de l'API</li>
      </ul>

      <h2>Documentation:</h2>
      <ul>
        <li><a href="/VERCEL_MIGRATION.md">Guide de migration Vercel</a></li>
        <li><a href="/DEPLOYMENT_SUMMARY.txt">Résumé de déploiement</a></li>
      </ul>
    </div>
  );
}
