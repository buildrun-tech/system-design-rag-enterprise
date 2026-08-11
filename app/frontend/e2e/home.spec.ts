import { test, expect } from '@playwright/test'

test('rota / carrega tela de login', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (err) => errors.push(err.message))
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })

  await page.goto('/')
  await page.waitForTimeout(1000)

  console.log('CONSOLE/PAGE ERRORS:', JSON.stringify(errors, null, 2))
  console.log('BODY HTML:', await page.locator('#root').innerHTML())

  await expect(page.locator('h1')).toBeVisible()
})
