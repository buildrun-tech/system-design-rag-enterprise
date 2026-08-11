import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserPool,
  CognitoUserSession,
} from 'amazon-cognito-identity-js'

const SESSION_STORAGE_KEY = 'notebooklm.directAuth.session'

// floci não valida o conteúdo do código de confirmação — qualquer valor confirma o usuário
const DUMMY_CONFIRMATION_CODE = '000000'

const userPool = new CognitoUserPool({
  UserPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
  ClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
  endpoint: import.meta.env.VITE_COGNITO_ENDPOINT,
})

export interface DirectAuthSession {
  accessToken: string
  idToken: string
  refreshToken: string
}

function toSession(cognitoSession: CognitoUserSession): DirectAuthSession {
  return {
    accessToken: cognitoSession.getAccessToken().getJwtToken(),
    idToken: cognitoSession.getIdToken().getJwtToken(),
    refreshToken: cognitoSession.getRefreshToken().getToken(),
  }
}

export function getStoredSession(): DirectAuthSession | null {
  const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
  return raw ? JSON.parse(raw) : null
}

function storeSession(session: DirectAuthSession) {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredSession() {
  sessionStorage.removeItem(SESSION_STORAGE_KEY)
}

export function signIn(email: string, password: string): Promise<DirectAuthSession> {
  const cognitoUser = new CognitoUser({ Username: email, Pool: userPool })
  const authDetails = new AuthenticationDetails({ Username: email, Password: password })

  return new Promise((resolve, reject) => {
    cognitoUser.authenticateUser(authDetails, {
      onSuccess: (cognitoSession) => {
        const session = toSession(cognitoSession)
        storeSession(session)
        resolve(session)
      },
      onFailure: reject,
    })
  })
}

export function signUp(email: string, password: string): Promise<void> {
  return new Promise((resolve, reject) => {
    userPool.signUp(email, password, [], [], (err) => {
      if (err) {
        reject(err)
        return
      }

      const cognitoUser = new CognitoUser({ Username: email, Pool: userPool })
      cognitoUser.confirmRegistration(DUMMY_CONFIRMATION_CODE, true, (confirmErr) => {
        if (confirmErr) {
          reject(confirmErr)
          return
        }
        resolve()
      })
    })
  })
}

export function signOut() {
  clearStoredSession()
}
