import { test, expect } from '@playwright/test'

test('cadastro direto não sofre CORS contra floci', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (err) => errors.push(err.message))
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('requestfailed', (req) => {
    errors.push(`REQUEST FAILED: ${req.method()} ${req.url()} — ${req.failure()?.errorText}`)
  })

  await page.goto('/')
  await page.waitForSelector('h1')

  const email = `e2e-${Date.now()}@example.com`
  await page.getByPlaceholder('email').fill(email)
  await page.getByPlaceholder('senha').fill('Teste@1234')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await page.waitForTimeout(2000)

  console.log('ERRORS:', JSON.stringify(errors, null, 2))
  const cognitoCorsError = errors.find((e) => /cognito-local/i.test(e) && /CORS|blocked|refused/i.test(e))
  expect(cognitoCorsError, `erro no cognito-local: ${cognitoCorsError}`).toBeUndefined()
})
