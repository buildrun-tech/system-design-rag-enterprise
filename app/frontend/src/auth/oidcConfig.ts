import type { AuthProviderProps } from 'react-oidc-context'
import { config } from '../config'

export const oidcConfig: AuthProviderProps = {
  authority: config.cognitoAuthority,
  client_id: config.cognitoClientId,
  redirect_uri: config.cognitoRedirectUri,
  response_type: 'code',
  scope: 'openid email profile',
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
