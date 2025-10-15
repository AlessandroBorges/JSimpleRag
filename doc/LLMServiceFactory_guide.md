# Guia: Como obter uma instância de `LLMService` usando `LLMServiceFactory`

Este documento explica, passo a passo, como criar uma instância de `LLMService` utilizando a classe `LLMServiceFactory` do projeto JSimpleLLM. O objetivo é mostrar como configurar e instanciar o serviço para diferentes provedores de LLM (Large Language Model), como OpenAI, Ollama e LM Studio.

---

## 1. O que é `LLMServiceFactory`?

A classe `LLMServiceFactory` é responsável por criar instâncias de serviços de LLM de acordo com o provedor desejado. Ela abstrai os detalhes de implementação e facilita a integração com diferentes APIs e servidores locais.

### Enumeração `SERVICE_PROVIDER`

A enumeração `SERVICE_PROVIDER` define os provedores suportados pela fábrica:

```java
public enum SERVICE_PROVIDER {
    OPENAI,
    ANTHROPIC,
    LM_STUDIO,
    OLLAMA,
    TOGETHER;
    // ...
}
```

Você pode converter uma string para o enum usando:

```java
SERVICE_PROVIDER provider = SERVICE_PROVIDER.fromString("OPENAI");
```

Cada valor representa um provedor de LLM, e pode ser usado para selecionar o serviço desejado na criação da instância.

---

## 2. O que é `LLMConfig`?

A classe `LLMConfig` representa a configuração do serviço LLM, incluindo URL base, token de autenticação, variáveis de ambiente, propriedades adicionais e modelos disponíveis.

Principais campos:
- `baseUrl`: URL do endpoint do serviço LLM.
- `apiToken`: Token de autenticação.
- `apiTokenEnvironment`: Nome da variável de ambiente para o token.
- `additionalProperties`: Mapa de propriedades extras para configurações avançadas.
- `modelMap`: Mapa de modelos disponíveis, cada um com nome, tipo e contexto.

Exemplo de uso:

```java
LLMConfig config = LLMConfig.builder()
    .baseUrl("https://api.openai.com/v1/")
    .apiToken("sua-api-key")
    .build();
```

Para adicionar modelos:

```java
config.getModelMap().put("gpt-4", new LLMConfig.Model("gpt-4", 8192, MODEL_TYPE.LANGUAGE, MODEL_TYPE.REASONING));
```

---

## 3. Principais métodos da fábrica

- `createLLMService(SERVICE_PROVIDER provider, LLMConfig config)`
- Métodos específicos para cada provedor, como `createOpenAI`, `createOllama`, `createLMStudio`, etc.

---

## 4. Passos para obter uma instância de `LLMService`

### Passo 1: Preparar a configuração (`LLMConfig`)

Você pode criar uma configuração personalizada para o serviço desejado:

```java
LLMConfig config = LLMConfig.builder()
    .baseUrl("https://api.openai.com/v1/")
    .apiToken("sua-api-key")
    .build();
```

Para provedores locais, como Ollama ou LM Studio, utilize as URLs e tokens apropriados:

```java
LLMConfig ollamaConfig = LLMConfig.builder()
    .baseUrl("http://localhost:11434/v1/")
    .apiToken("ollama")
    .build();
```

### Passo 2: Escolher o provedor

Utilize o enum `SERVICE_PROVIDER` para indicar o serviço desejado:

```java
LLMServiceFactory.SERVICE_PROVIDER provider = LLMServiceFactory.SERVICE_PROVIDER.OPENAI;
```

Ou via string:
```java
LLMServiceFactory.SERVICE_PROVIDER provider = LLMServiceFactory.SERVICE_PROVIDER.fromString("OLLAMA");
```

### Passo 3: Criar a instância do serviço

Use o método estático da fábrica para obter a instância:

```java
LLMService llmService = LLMServiceFactory.createLLMService(provider, config);
```

#### Exemplos para cada provedor

**OpenAI:**

```java
LLMService llmService = LLMServiceFactory.createLLMService(
    LLMServiceFactory.SERVICE_PROVIDER.OPENAI,
    config
);
```

**Ollama (local):**

```java
LLMService llmService = LLMServiceFactory.createLLMService(
    LLMServiceFactory.SERVICE_PROVIDER.OLLAMA,
    ollamaConfig
);
```

**LM Studio (local):**

```java
LLMService llmService = LLMServiceFactory.createLLMService(
    LLMServiceFactory.SERVICE_PROVIDER.LM_STUDIO,
    lmStudioConfig
);
```

### Passo 4: Utilizar o serviço

Após obter a instância, você pode utilizar os métodos do `LLMService` para realizar operações como geração de texto, chat, embeddings, etc.

```java
String resposta = llmService.complete("Explique a teoria da relatividade.");
System.out.println(resposta);
```

---

## 5. Observações importantes

- Para provedores compatíveis com OpenAI (Anthropic, Together), utilize o mesmo método e configuração, apenas alterando o enum do provedor.
- Se não passar uma configuração, alguns métodos da fábrica criam instâncias com configurações padrão.
- Consulte a documentação de cada implementação para detalhes específicos de parâmetros e modelos.
- A configuração dos modelos pode ser feita via `LLMConfig` para customizar o comportamento do serviço.

---

## 6. Referências

- [`LLMServiceFactory.java`](src/main/java/bor/tools/simplellm/LLMServiceFactory.java)
- [`LLMConfig.java`](src/main/java/bor/tools/simplellm/LLMConfig.java)
- [`LLMService.java`](src/main/java/bor/tools/simplellm/LLMService.java)

---

Este guia cobre o processo completo para obter e utilizar uma instância de `LLMService` via `LLMServiceFactory`, detalhando o uso da enumeração `SERVICE_PROVIDER` e da configuração `LLMConfig`. Para dúvidas ou exemplos avançados, consulte os arquivos de implementação ou o README do projeto.