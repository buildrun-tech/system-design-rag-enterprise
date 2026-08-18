## MODIFIED Requirements

### Requirement: Tokens visuais derivados de DESIGN.md
O sistema SHALL definir as custom properties de cor, tipografia, espaçamento e raio em `index.css` a partir dos valores atualizados em DESIGN.md (accent primário `#0d0d0d` — preto, radius 8px, grid base 8px, fonte `system-ui`), substituindo qualquer token de scaffold não relacionado. O dark mode SHALL usar uma paleta monocromática invertida (preto↔branco), sem introduzir cor de acento distinta do light mode.

#### Scenario: Inspecionar tokens de cor
- **WHEN** um componente lê a custom property `--accent` (ou equivalente de primary)
- **THEN** o valor resolvido corresponde ao primary definido em DESIGN.md (`#0d0d0d`), não ao roxo-acinzentado anterior (`#8e8ea0`)

#### Scenario: Inspecionar tokens de cor no dark mode
- **WHEN** o sistema opera em `prefers-color-scheme: dark`
- **THEN** `--accent` resolve pra um tom monocromático (branco/cinza-claro), não pro roxo vibrante anterior (`#c084fc`)

### Requirement: Componente Button reutilizável
O sistema SHALL fornecer um componente `Button` em `components/ui/` usando os tokens de cor, radius e tipografia do DESIGN.md, substituindo os elementos `<button>` nativos sem estilo nas telas existentes. O botão SHALL exibir um estado visual distinto de hover e active (além de mudança de opacidade), e a variante primary SHALL ter uma sombra sutil.

#### Scenario: Renderizar botão primário
- **WHEN** uma tela renderiza `<Button>` sem variante explícita
- **THEN** o botão aplica cor de fundo primary (`#0d0d0d`), texto on-primary, radius 8px, sombra sutil e transição de 400ms definidos em DESIGN.md

#### Scenario: Hover em botão primário
- **WHEN** o usuário passa o mouse sobre `<Button>` (variante primary ou secondary)
- **THEN** o botão aplica uma mudança visível de cor de fundo ou borda, distinta do estado padrão e do estado `:disabled`

## ADDED Requirements

### Requirement: Ausência de estilo inline nas páginas
As páginas do frontend SHALL usar classes CSS (tokens do sistema de design ou classes utilitárias em `ui.css`) em vez de `style={{}}` inline para layout e cor, garantindo que qualquer mudança de tema se propague sem editar componentes de página.

#### Scenario: Renderizar tela de login
- **WHEN** `LoginPage` é renderizada
- **THEN** nenhum elemento usa a prop `style` para definir cor, layout flex ou espaçamento — todos usam classes do sistema `ui/`
