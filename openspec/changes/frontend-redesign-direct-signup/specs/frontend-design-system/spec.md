## ADDED Requirements

### Requirement: Tokens visuais derivados de DESIGN.md
O sistema SHALL definir as custom properties de cor, tipografia, espaçamento e raio em `index.css` a partir dos valores medidos em DESIGN.md (primary `#8e8ea0`, radius 5px, grid base 8px, fonte `system-ui`), substituindo qualquer token de scaffold não relacionado.

#### Scenario: Inspecionar tokens de cor
- **WHEN** um componente lê a custom property `--accent` (ou equivalente de primary)
- **THEN** o valor resolvido corresponde ao primary definido em DESIGN.md (`#8e8ea0`), não ao valor de scaffold anterior (`#aa3bff`)

### Requirement: Componente Button reutilizável
O sistema SHALL fornecer um componente `Button` em `components/ui/` usando os tokens de cor, radius e tipografia do DESIGN.md, substituindo os elementos `<button>` nativos sem estilo nas telas existentes.

#### Scenario: Renderizar botão primário
- **WHEN** uma tela renderiza `<Button>` sem variante explícita
- **THEN** o botão aplica cor de fundo primary, texto on-primary, radius 5px e transição de 400ms definidos em DESIGN.md

### Requirement: Componente Input reutilizável
O sistema SHALL fornecer um componente `Input` em `components/ui/` com estilo consistente aos tokens do DESIGN.md, substituindo elementos `<input>` nativos sem estilo nas telas existentes.

#### Scenario: Renderizar campo de formulário
- **WHEN** uma tela renderiza `<Input>`
- **THEN** o campo aplica radius, cor de borda e tipografia (`body`, `system-ui`) definidos em DESIGN.md

### Requirement: Componente Card reutilizável
O sistema SHALL fornecer um componente `Card` em `components/ui/` para agrupar conteúdo (ex: item de notebook na listagem), seguindo espaçamento em múltiplos de 8px e sem sombra decorativa, conforme DESIGN.md.

#### Scenario: Renderizar item de notebook
- **WHEN** `NotebooksPage` renderiza um item da lista de notebooks
- **THEN** o item usa o componente `Card` com padding em múltiplo de 8px e sem `box-shadow`
