import { expect, test } from '@playwright/test'

// Customer domains go through the mock proxy (see playwright.config.ts).
const customerUrl = (host: string, path = '/') => `http://${host}${path}`

test('connect a domain, serve the portal through the proxy, remove it', async ({
  page,
}) => {
  const host = `billing-${Date.now()}.acme.test`
  const supplier = page.getByTestId('supplier-acme')

  await page.goto('/')
  await supplier.getByLabel('Domain', { exact: true }).fill(host)
  await supplier.getByLabel('DNS contact email (optional)').fill('it@acme.test')
  await supplier.getByRole('button', { name: 'Add domain' }).click()
  const row = page.getByTestId(`domain-${host}`)
  await expect(row).toContainText('Waiting for DNS')

  // The mock marks *.test as pointed after ~2s and sends webhooks.
  await expect(async () => {
    await page.reload()
    await expect(row).toContainText('Live')
  }).toPass({ timeout: 15_000 })

  await page.goto(customerUrl(host))
  await expect(page.getByRole('heading', { name: 'Acme Supplies' })).toBeVisible()
  await expect(page).toHaveTitle('Acme Supplies client portal')
  // Built with ServletUriComponentsBuilder: the customer's domain, not
  // the app's own host.
  await expect(page.getByTestId('portal-url')).toHaveText(customerUrl(host))

  // A form with Spring Security's CSRF token, through the proxy. The
  // redirect after it keeps the browser on the customer's domain.
  await page.getByLabel('Invoice').selectOption('INV-1042')
  await page.getByLabel('Email').fill('ap@client.test')
  await page.getByRole('button', { name: 'Request a copy' }).click()
  await expect(page.getByRole('status')).toHaveText(
    'Thanks. Acme Supplies will email a copy of INV-1042 to ap@client.test.',
  )
  await expect(page).toHaveURL(customerUrl(host))

  await page.goto('/')
  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(row).toBeHidden()
  const gone = await page.goto(customerUrl(host))
  expect(gone?.status()).toBe(404)
})

test('a domain that does not point at us stays pending', async ({ page }) => {
  const host = `invoices-${Date.now()}.globex.example`
  const supplier = page.getByTestId('supplier-globex')

  await page.goto('/')
  await supplier.getByLabel('Domain', { exact: true }).fill(host)
  await supplier.getByRole('button', { name: 'Add domain' }).click()
  const row = page.getByTestId(`domain-${host}`)
  await row.getByRole('button', { name: 'Check DNS' }).click()
  await expect(row).toContainText('Waiting for DNS')

  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(row).toBeHidden()
})

test('rejects our own domains and invalid input', async ({ page }) => {
  const supplier = page.getByTestId('supplier-acme')
  await page.goto('/')
  await supplier.getByLabel('Domain', { exact: true }).fill('admin.billport.example')
  await supplier.getByRole('button', { name: 'Add domain' }).click()
  await expect(supplier.getByRole('alert')).toHaveText("That's one of our domains.")

  await supplier.getByLabel('Domain', { exact: true }).fill('not a domain')
  await supplier.getByRole('button', { name: 'Add domain' }).click()
  await expect(supplier.getByRole('alert')).toHaveText("That isn't a valid domain.")
})

test('requests that skip the proxy cannot claim a domain', async ({
  request,
}) => {
  const spoofed = await request.get('/', {
    headers: { 'X-Served-For': 'billing.acme.test' },
  })
  expect(spoofed.status()).toBe(403)

  const wrongToken = await request.get('/', {
    headers: {
      'X-Served-For': 'billing.acme.test',
      'X-SaaS-Domains-Auth-Token': 'guess',
    },
  })
  expect(wrongToken.status()).toBe(403)
})

test('webhooks need the right token', async ({ request }) => {
  const res = await request.post('/webhooks/saascustomdomains?token=x', {
    data: { event: 'domain_dns_proxy_record_valid', domain: { uuid: 'x' } },
  })
  expect(res.status()).toBe(401)
})

test('unknown domains get a 404', async ({ page }) => {
  const res = await page.goto(customerUrl('unknown.test'))
  expect(res?.status()).toBe(404)
})
