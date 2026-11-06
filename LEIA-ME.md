# Voz em Texto — Android nativo 1.1.0

**Este repositório é um aplicativo Android real escrito em Kotlin**, não é uma página HTML, PWA ou aplicativo JavaScript. O HTML anterior era **somente uma simulação visual**, não um aplicativo instalável.

## Como gerar o APK no GitHub

Siga `GUIA-GITHUB-ANDROID.md` no mesmo ZIP. O workflow automático está em `.github/workflows/build-apk.yml` e gera o arquivo `app-debug.apk` quando compilado com sucesso.

> **Estado de verificação:** código-fonte preparado e inspecionado; o APK **ainda não foi compilado neste ambiente, nem testado em um aparelho**. A execução do GitHub Actions será a primeira verificação de compilação. Erros de build ou funcionamento devem ser corrigidos a partir do log de Actions ou do teste no celular.

## Recursos incluídos no código

- **Voz pelo microfone:** transcrição utilizando captura PCM do Android e motor de reconhecimento local Vosk.
- **Áudio reproduzido em outros apps:** `MediaProjection` + `AudioPlaybackCapture` no Android 10+; captura somente áudios não protegidos e explicitamente permitidos pelo app de origem.
- **Dois modos de exibição:** prévia ao vivo (palavras provisórias, que podem ser corrigidas) ou trechos reconhecidos após pausas.
- **Segundo plano:** serviço Android em primeiro plano, com notificação persistente e ação **Parar**. Em certos celulares, restrições de bateria podem interrompê-lo.
- **Janela flutuante opcional:** aparece sobre outros apps quando autorizada, pode ser arrastada e recolhida em uma bolha; ou fica desligada, sem janela visível.
- **Texto persistente:** salvo automaticamente no armazenamento privado do app; copiar, salvar como TXT, compartilhar, apagar com confirmação.
- **Preferências lembradas:** fonte de som, modo de exibição, janela flutuante.
- **Interface adaptada às barras de navegação:** ajustes de área segura para Android 15+.
- **Permissões mínimas e explícitas:** microfone, notificação quando aplicável, janela flutuante se escolhida e autorização de captura de tela/áudio a cada sessão interna.

## Primeiro uso depois da instalação

1. Abra **Voz em Texto** e escolha **Minha voz** ou **Áudio do celular**.
2. Toque **Iniciar transcrição**. Autorize o download inicial do modelo de português (cerca de 31 MB); aguarde concluir e toque **Iniciar** novamente.
3. Conceda a permissão de áudio solicitada. Para áudio do celular, aceite também o aviso de captura do próprio Android **toda vez que iniciar uma nova sessão**.
4. Em **⚙ Configurações**, escolha **prévia ao vivo** ou **frases completas** e **com janela** ou **sem janela**. O próprio aplicativo explica como autorizar a sobreposição.
5. Abra outro aplicativo enquanto a transcrição continuar ativa; use a notificação ou a tela do Voz em Texto para parar.
6. Use **Copiar**, **Salvar TXT** ou **Enviar**. O texto final permanece no app até você o apagar.

## Ambiente Android e bibliotecas

- Kotlin 1.9.25, Java 17, Android Gradle Plugin 8.6.1, Gradle 8.7.
- SDK para compilar 35; Android mínimo 10 (API 29); destino 35.
- Biblioteca `com.alphacephei:vosk-android:0.3.47` (Maven Central).
- Modelo oficial `vosk-model-small-pt-0.3`: `https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip`, baixado **pelo app após consentimento** e extraído em pasta privada. Não vem no APK.
- Transcrição **offline** após baixar o modelo; o áudio capturado não é enviado a serviço remoto de transcrição.
- O projeto utiliza Android SDK e APIs nativas, sem WebView, HTML ou JavaScript como base do aplicativo.

## Limites importantes

- O reconhecimento de fala pode errar palavras, principalmente com barulho e em áudios comprimidos; a pontuação automática é simples.
- Alguns vídeos, chamadas, aplicativos de streaming e conteúdos com restrição de cópia **não podem ser capturados**, mesmo concedendo todas as permissões. Não há como contornar isso com root, Shizuku ou permissões extras comuns.
- O modo “palavra por palavra” mostra **hipóteses provisórias** que mudam durante a fala, e não garante publicar cada palavra instantaneamente.
- O Android sempre poderá mostrar avisos e indicadores quando microfone/projeção estiverem ativos; o app não grava de modo oculto.
- O modelo Vosk costuma usar uma quantidade considerável de memória durante a transcrição; desempenho depende do telefone.
- Código-fonte experimental para teste pessoal. Ainda falta **compilar e validar no aparelho real**.

## Estrutura

- `app/src/main/java/com/vozemtexto/app` — Kotlin (tela, permissões, captura, reconhecimento, overlay e armazenamento).
- `app/src/main/res` — layouts e tema do Android.
- `app/src/main/AndroidManifest.xml` — componentes e permissões.
- `.github/workflows/build-apk.yml` — compilação do APK via GitHub Actions.
- `GUIA-GITHUB-ANDROID.md` — tutorial de upload e compilação pelo celular.

## Testar pelo Android Studio (opcional)

Descompacte e abra a pasta raiz com o Android Studio, use Java 17 / Gradle 8.7 e o SDK Android 35, depois execute `:app:assembleDebug`. Este ZIP **não inclui o Gradle Wrapper**, porque o workflow no GitHub instala a versão exata do Gradle. O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
