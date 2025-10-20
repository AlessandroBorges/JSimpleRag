# Como Usar Claude Code com IDE Externo

Claude Code pode ser integrado com IDEs externos para fornecer uma experiência de desenvolvimento aprimorada. Aqui está um guia completo:

## Configuração Básica

### 1. Conectar Claude Code ao IDE

Para conectar o Claude Code rodando em um terminal externo ao seu IDE:

```bash
# No terminal onde Claude Code está rodando, use:
/ide
```

### 2. Configurar o Diff Tool

```bash
# Entre no modo de configuração:
/config

# Configure o diff tool para auto (detecção automática):
# Defina diff tool como "auto"
```

## IDEs Suportados

A integração via CLI funciona com:
- **Visual Studio Code**
- **Cursor**
- **Windsurf**
- **VSCodium**
- **JetBrains IDEs** (via plugin beta)
- **Emacs** (via extensão de terceiros)

## Recursos Disponíveis

Ao conectar Claude Code ao seu IDE, você obtém:

### 1. Compartilhamento Automático de Contexto
- A seleção atual e a aba ativa no IDE são sincronizadas automaticamente com Claude
- Claude tem acesso ao arquivo que você está editando

### 2. Visualização de Diff no IDE
- Mudanças de código são exibidas diretamente no diff viewer do IDE
- Não precisa ver diffs no terminal

### 3. Atalhos para Referências de Arquivos
- **Mac**: `Cmd+Option+K`
- **Windows/Linux**: `Alt+Ctrl+K`
- Insere referências como `@File#L1-99` rapidamente

### 4. Compartilhamento de Diagnósticos
- Erros de lint e sintaxe do IDE são automaticamente compartilhados com Claude
- Claude pode ver e corrigir os "squiggles vermelhos"

### 5. Lançamento Rápido
- **Mac**: `Cmd+Esc`
- **Windows/Linux**: `Ctrl+Esc`
- Abre Claude Code diretamente do editor

## Opções de Integração

### Opção 1: CLI em Terminal Externo (Recomendado para Eclipse)
Esta é a abordagem mais flexível para IDEs como Eclipse:
1. Abra um terminal externo
2. Execute Claude Code
3. Use `/ide` para conectar ao seu editor
4. Continue trabalhando no Eclipse enquanto Claude acessa os arquivos

### Opção 2: Extensões Nativas
- **VS Code**: Extensão oficial disponível no Marketplace
- **JetBrains**: Plugin beta com integração do Claude Agent
- **Emacs**: claude-code-ide.el via Model Context Protocol (MCP)

## Fluxo de Trabalho Recomendado

1. **Inicie seu IDE** (Eclipse, IntelliJ, VS Code, etc.)
2. **Abra um terminal** externo na pasta do projeto
3. **Execute Claude Code** no terminal
4. **Conecte ao IDE** com `/ide`
5. **Trabalhe normalmente** no IDE enquanto Claude observa e auxilia

## Considerações para JSimpleRag

Dado que você está trabalhando com Java/Spring Boot no Eclipse:

```bash
# No terminal do projeto:
cd /mnt/f/1-ProjetosIA/github/JSimpleRag
claude-code  # ou o comando que você usa para iniciar

# Dentro do Claude Code:
/ide  # Conecta ao editor externo
```

Com isso, Claude Code poderá:
- Ler e modificar arquivos Java
- Executar comandos Maven (`./mvnw`)
- Ver erros de compilação do Eclipse
- Sugerir e aplicar correções diretamente nos arquivos

Você continua usando Eclipse normalmente, e Claude Code funciona como um assistente paralelo no terminal.
