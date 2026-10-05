// window.__APP_CONFIG__ vem de /config.js, gerado no deploy a partir dos outputs do Terraform
// (mesmo dist/ serve dev e prod). Sem ele (dev local, e2e), cai no .env via import.meta.env.
interface AppConfig {
  apiBaseUrl: string
  cognitoAuthority: string
  cognitoClientId: string
  cognitoUserPoolId: string
  cognitoRedirectUri: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppConfig>
  }
}

const runtime = window.__APP_CONFIG__ ?? {}

export const config: AppConfig = {
  apiBaseUrl: runtime.apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL,
  cognitoAuthority: runtime.cognitoAuthority ?? import.meta.env.VITE_COGNITO_AUTHORITY,
  cognitoClientId: runtime.cognitoClientId ?? import.meta.env.VITE_COGNITO_CLIENT_ID,
  cognitoUserPoolId: runtime.cognitoUserPoolId ?? import.meta.env.VITE_COGNITO_USER_POOL_ID,
  cognitoRedirectUri: runtime.cognitoRedirectUri ?? import.meta.env.VITE_COGNITO_REDIRECT_URI,
}
