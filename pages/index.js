export default function Home() {
  return (
    <main
      style={{
        minHeight: '100vh',
        padding: '56px 24px',
        fontFamily: 'Arial, sans-serif',
        background: 'linear-gradient(135deg, #f6f8ff 0%, #eef4ff 100%)',
        color: '#13213c'
      }}
    >
      <section style={{ maxWidth: '880px', margin: '0 auto' }}>
        <div
          style={{
            display: 'inline-block',
            padding: '8px 14px',
            borderRadius: '999px',
            background: '#1f4fff',
            color: '#fff',
            fontSize: '14px',
            fontWeight: 700,
            letterSpacing: '0.04em',
            textTransform: 'uppercase'
          }}
        >
          Sarbi Rohek
        </div>

        <h1 style={{ fontSize: 'clamp(2.2rem, 5vw, 4rem)', lineHeight: 1.05, margin: '20px 0 16px' }}>
          Bienvenue sur la plateforme de services
        </h1>

        <p style={{ fontSize: '1.15rem', maxWidth: '720px', lineHeight: 1.7, margin: '0 0 32px' }}>
          Trouvez des prestataires, suivez vos demandes et gérez vos échanges depuis un espace simple et centralisé.
        </p>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '14px', marginBottom: '40px' }}>
          <a
            href="/login.html"
            style={{
              padding: '14px 22px',
              borderRadius: '12px',
              background: '#1f4fff',
              color: '#fff',
              textDecoration: 'none',
              fontWeight: 700
            }}
          >
            Connexion
          </a>
          <a
            href="/register.html"
            style={{
              padding: '14px 22px',
              borderRadius: '12px',
              background: '#ffffff',
              color: '#13213c',
              textDecoration: 'none',
              fontWeight: 700,
              border: '1px solid rgba(19, 33, 60, 0.15)'
            }}
          >
            Inscription
          </a>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '16px'
          }}
        >
          <article style={{ padding: '20px', borderRadius: '18px', background: '#fff', boxShadow: '0 12px 30px rgba(31, 79, 255, 0.08)' }}>
            <h2 style={{ marginTop: 0 }}>Clients</h2>
            <p style={{ marginBottom: 0, lineHeight: 1.6 }}>
              Consultez les prestataires, envoyez des messages et suivez vos commandes.
            </p>
          </article>

          <article style={{ padding: '20px', borderRadius: '18px', background: '#fff', boxShadow: '0 12px 30px rgba(31, 79, 255, 0.08)' }}>
            <h2 style={{ marginTop: 0 }}>Prestataires</h2>
            <p style={{ marginBottom: 0, lineHeight: 1.6 }}>
              Gérez vos demandes, vos disponibilités et vos notifications en un seul endroit.
            </p>
          </article>

          <article style={{ padding: '20px', borderRadius: '18px', background: '#fff', boxShadow: '0 12px 30px rgba(31, 79, 255, 0.08)' }}>
            <h2 style={{ marginTop: 0 }}>API</h2>
            <p style={{ marginBottom: 0, lineHeight: 1.6 }}>
              Le backend reste disponible pour les routes `/api/*` et les écrans frontend se chargent séparément.
            </p>
          </article>
        </div>

        <p style={{ marginTop: '28px', color: '#5b6780' }}>
          Backend Render connecté au frontend. Si tu veux, on peut maintenant masquer cette page et pointer directement vers l'écran de connexion.
        </p>
      </section>
    </main>
  );
}
