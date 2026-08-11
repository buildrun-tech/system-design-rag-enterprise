import { test, expect, type Page } from '@playwright/test'

function trackErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`))
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(`console: ${msg.text()}`)
  })
  return errors
}

test('login -> notebooks -> workspace sem erro no console', async ({ page }) => {
  const errors = trackErrors(page)

  await page.goto('/')
  await page.waitForSelector('h1')

  const email = `console-check-${Date.now()}@example.com`
  await page.getByPlaceholder('email').fill(email)
  await page.getByPlaceholder('senha').fill('Teste@1234')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await page.waitForURL('**/notebooks', { timeout: 10000 })
  await page.waitForTimeout(1000)
  console.log('ERRORS AFTER /notebooks:', JSON.stringify(errors, null, 2))
  expect(errors, `erros em /notebooks: ${JSON.stringify(errors)}`).toEqual([])

  await page.getByPlaceholder('Nome do notebook').fill('e2e workspace notebook')
  await page.getByRole('button', { name: 'criar' }).click()
  await page.waitForTimeout(1000)

  await page.getByRole('button', { name: 'abrir' }).first().click()
  await page.waitForURL('**/notebooks/*', { timeout: 10000 })
  await page.waitForTimeout(1500)

  console.log('ERRORS AFTER /notebooks/:id:', JSON.stringify(errors, null, 2))
  expect(errors, `erros em /notebooks/:id: ${JSON.stringify(errors)}`).toEqual([])
})
