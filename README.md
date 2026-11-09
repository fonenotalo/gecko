# Gecko

Player de música Android feito em Kotlin + Jetpack Compose. Interface limpa, tela de reprodução com a arte em destaque, cores tiradas da capa, temas claro/escuro e navegação pensada para uso com uma mão.

Gecko toca a sua biblioteca local, aceita fontes lossless adicionais por complementos e pode conversar com uma ponte auto-hospedada de YouTube Music. Não tem anúncios, não exige conta e não envia suas músicas locais para lugar nenhum.

> **Status:** projeto em desenvolvimento. O APK de depuração é gerado pelo workflow do GitHub Actions; não há build assinado de produção.

## O que já está no app

- **Biblioteca única** com músicas, álbuns e artistas do aparelho, busca local e busca remota nas fontes conectadas.
- **Player completo** com arte grande, paleta extraída da capa, seek, embaralhar, repetir, fila editável, letra em tela cheia e folha de qualidade.
- **Qualidade real, não prometida.** O selo de qualidade vem do `Format`/`Tracks` do Media3, então o app mostra o que o decoder entregou de verdade — inclusive quando a fonte remota manda algo abaixo do pedido.
- **Downloads offline** com progresso, retry, exclusão individual/total, limite por Wi-Fi e downloads inteligentes das próximas faixas.
- **Letras `.lrc`** importadas pelo seletor de arquivos e salvas somente no armazenamento privado do app.
- **Modo Karaokê** com letra sincronizada, auto-scroll e palco animado nas cores da capa.
- **Modo iPod clássico** com click wheel (toque por zonas + arrasto), menus de músicas/álbuns/artistas, tela "tocando agora" e visual retrô.
- **Mix infinito** que continua a fila a partir de uma faixa ou do histórico recente.
- **Temas** automático/claro/escuro, estilo moderno ou iPod clássico, e opção de usar as cores da arte do álbum em toda a interface.
- **Conteúdo de demonstração** com duas faixas WAV PCM 16-bit/44,1 kHz de 30 s, capas e letras — útil para testar sem músicas locais.

## Integrações opcionais

Todas as integrações abaixo são desligadas por padrão e ficam sob seu controle. O Gecko é o cliente: ele não hospeda catálogo, não faz scraping e não contorna DRM.

### YouTube Music (ponte auto-hospedada)

Configure a URL e, se quiser, um token em **Ajustes → YouTube Music**. A ponte precisa expor:

```text
GET {base}/api/health
GET {base}/api/search?q={query}
GET {base}/api/stream/{id}?quality={quality}
```

Resposta de busca (também aceita `results` ou `items`):

```json
{
  "tracks": [
    {
      "id": "abc123",
      "title": "Faixa",
      "artist": "Artista",
      "album": "Álbum",
      "durationMs": 245000,
      "artworkUrl": "https://exemplo.com/capa.jpg",
      "lossless": true,
      "quality": "FLAC 24-bit/96 kHz"
    }
  ]
}
```

Resposta de stream (texto puro com URL ou JSON):

```json
{ "url": "https://exemplo.com/audio.flac" }
```

O token, quando preenchido, é enviado como `Authorization: Bearer ...` e `X-API-Key`.

### Complementos lossless

Um complemento é apenas um **manifesto JSON** importado em **Ajustes → Complementos lossless**. Nenhum código é executado. O arquivo fica em `files/gecko_plugins` e pode ser removido a qualquer momento.

Exemplo em [`docs/plugin-example.json`](docs/plugin-example.json):

```json
{
  "id": "meu-acervo-lossless",
  "name": "Meu Acervo Lossless",
  "version": "1.0.0",
  "baseUrl": "https://musica.exemplo.com",
  "apiKey": "",
  "supportsLossless": true,
  "searchPath": "/api/search?q={query}",
  "streamPath": "/api/stream/{id}?quality={quality}"
}
```

- `{query}` é substituído pela busca já codificada para URL.
- `{id}` é o `remoteId` da faixa.
- `{quality}` é a chave da qualidade escolhida (`saver`, `normal`, `high`, `lossless`, `hires`).
- A busca deve devolver o mesmo formato de `tracks` descrito acima.
- Use apenas fontes que você tem direito de acessar na sua região.

### Last.fm

Scrobbling opcional via API oficial. Preencha **API key**, **API secret** e a **session key** obtida no fluxo de autenticação do Last.fm. O botão **Validar sessão** chama `user.getInfo`; o Gecko nunca pede sua senha.

### Discord

O Discord não expõe Rich Presence para apps Android de terceiros. O Gecko envia a faixa atual em JSON para uma **bridge** que você mantém em um computador com o Discord conectado:

```text
POST {bridgeUrl}
Content-Type: application/json
```

```json
{
  "app": "Gecko",
  "active": true,
  "title": "Faixa",
  "artist": "Artista",
  "album": "Álbum",
  "source": "LOCAL",
  "isPlaying": true,
  "positionMs": 42000,
  "durationMs": 245000,
  "artworkUrl": "",
  "startTimestamp": 1720000000000,
  "endTimestamp": 1720000203000
}
```

Sem a bridge, a opção fica desligada e nada é enviado.

## Privacidade

- Músicas, capas e letras locais ficam no aparelho.
- Nenhum servidor do Gecko é usado: as integrações falam direto com as URLs que você configura.
- O app pede permissão de notificações (Android 13+) apenas para o controle de reprodução.
- Downloads e banco de configurações usam o armazenamento privado do app.

## Como compilar

Requisitos:

- Android Studio recente (Koala ou mais novo) **ou** JDK 17 + Android SDK 35.
- `minSdk 26`, `targetSdk 35`, Kotlin 2.0.20, AGP 8.5.2, Gradle 8.7.

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Para gerar um build de produção é preciso configurar sua própria assinatura (`signingConfigs`) e remover/ajustar as regras de minificação em `app/proguard-rules.pro`.

### CI

O workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) roda em push/PR e publica o APK de depuração como artefato:

1. JDK 17 (Temurin)
2. Android SDK com `platforms;android-35` e `build-tools;35.0.0`
3. `./gradlew assembleDebug`
4. Upload de `app/build/outputs/apk/debug/*.apk`

## Estrutura do projeto

```text
app/src/main/java/com/gecko/app/
├── data/            biblioteca local, letras, downloads, demo, arte
├── di/              AppContainer (injeção manual)
├── integrations/    Last.fm e bridge do Discord
├── model/           Track, Album, Artist, AppSettings, qualidade, paleta
├── navigation/      rotas do Compose Navigation
├── playback/        Media3/ExoPlayer, MediaSessionService e qualidade real
├── sources/         ponte YouTube Music, complementos JSON, parser remoto
├── ui/              ViewModel, tema, componentes e telas
│   ├── components/  player em miniatura, capas, cartões, fileiras e qualidade
│   └── screens/     Início, Biblioteca, Downloads, Ajustes, Player, Karaokê, iPod
└── util/            formatação e utilidades
```

A injeção de dependências é manual (`AppContainer`), a persistência usa DataStore/SharedPreferences e JSON, e o playback usa Media3 com um `MediaSessionService`.

## Limitações conhecidas

- **YouTube Music** exige uma ponte autorizada; o app não implementa scraping, login ou bypass de DRM.
- **Discord** exige uma bridge em um computador, porque Rich Presence não é exposto a apps Android de terceiros.
- **Qualidade lossless** depende da fonte. O app mostra a qualidade decodificada, mesmo que seja menor que a solicitada.
- **Downloads** dependem das fontes configuradas; arquivos baixados ficam no armazenamento privado do app e não aparecem no gerenciador de arquivos do sistema.
- **Letras** são importadas manualmente em `.lrc`; o app não baixa letras de serviços de terceiros.
- O projeto não inclui catálogo, credenciais, chaves de API nem assinatura de produção.

## Legal

Gecko não fornece, hospeda ou distribui conteúdo musical. Você é responsável pelas fontes que configura e deve respeitar os termos de uso e a legislação da sua região. O uso de complementos e pontes de terceiros é de sua responsabilidade.

## Créditos

- Fonte **Manrope** (SIL Open Font License 1.1): [`LICENSES/OFL-Manrope.txt`](LICENSES/OFL-Manrope.txt).
- Ícones do Material Symbols/Material Icons (Apache 2.0).
- Jetpack Compose, Media3, Coil, OkHttp e demais bibliotecas sob suas respectivas licenças.
