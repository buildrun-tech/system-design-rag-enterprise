import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserPool,
  CognitoUserSession,
} from 'amazon-cognito-identity-js'
import { config } from '../config'

const SESSION_STORAGE_KEY = 'notebooklm.directAuth.session'

// floci não valida o conteúdo do código de confirmação — qualquer valor confirma o usuário
const DUMMY_CONFIRMATION_CODE = '000000'

// lazy: construtor lança sem UserPoolId/ClientId — instanciar no import derrubaria o app
// em ambientes sem direct auth (CloudFront/AWS usa só Hosted UI)
let userPool: CognitoUserPool | undefined

function getUserPool(): CognitoUserPool {
  userPool ??= new CognitoUserPool({
    UserPoolId: config.cognitoUserPoolId,
    ClientId: config.cognitoClientId,
    endpoint: import.meta.env.VITE_COGNITO_ENDPOINT,
  })
  return userPool
}

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
  const cognitoUser = new CognitoUser({ Username: email, Pool: getUserPool() })
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
    getUserPool().signUp(email, password, [], [], (err) => {
      if (err) {
        reject(err)
        return
      }

      const cognitoUser = new CognitoUser({ Username: email, Pool: getUserPool() })
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
