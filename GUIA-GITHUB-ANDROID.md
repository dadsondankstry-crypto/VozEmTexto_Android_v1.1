# Como transformar o projeto em APK pelo GitHub Actions

**Atenção: não envie o ZIP inteiro como um único arquivo ao repositório.** Primeiro **descompacte** o arquivo ZIP e envie os arquivos/pastas de dentro da pasta `VozEmTexto-Android` para a **raiz** do repositório.

## Passo a passo no celular

1. Baixe `VozEmTexto_Android_Nativo_v1.1_GitHub.zip` e use o gerenciador de arquivos do seu telefone para **Extrair / Descompactar**.
2. Acesse GitHub.com no navegador e crie um **repositório novo** (pode ser **Private**). Evite gerar outro README inicial para ficar mais fácil subir os arquivos.
3. Faça upload da estrutura de pastas: `app/`, `.github/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `LEIA-ME.md` e `GUIA-GITHUB-ANDROID.md`. A pasta **`.github` começa com ponto e pode ficar oculta** em alguns gerenciadores de arquivos; configure para mostrar arquivos ocultos.
4. Confirme que o caminho **exato** `.github/workflows/build-apk.yml` aparece no repositório. Se esse arquivo faltar, o GitHub não vai mostrar o botão para gerar APK.
5. Abra a aba **Actions**, selecione **Gerar APK de teste**, toque **Run workflow** e escolha a branch `main`. Se houver um build iniciado automaticamente ao enviar os arquivos, você também pode aguardar esse processo.
6. Abra a execução mais recente do workflow e aguarde a etapa **Compilar APK Android nativo**. Quando os passos estiverem verdes, role até **Artifacts** e baixe **VozEmTexto-APK-para-instalar**.
7. Extraia o arquivo baixado dos artifacts e localize **`app-debug.apk`**. Toque nele para instalar no telefone. Se o Android pedir permissão de **instalar apps desconhecidos** para o navegador/gerenciador de arquivos, libere apenas a fonte que está usando e confirme a instalação.
8. Abra o app, baixe o modelo offline na primeira execução e autorize somente as permissões solicitadas.

## Caso apareça uma falha vermelha

Abra **Actions → execução com erro → apk → Compilar APK Android nativo** e copie as linhas de erro da compilação (sobretudo a parte de `FAILURE: Build failed` e o `* What went wrong:`). Mande o texto ou uma captura no chat para corrigirmos o projeto.

## Importante

- **HTML é só prévia da aparência**, não é o aplicativo real. Este ZIP contém **projeto Kotlin nativo**.
- O GitHub **não compila automaticamente um ZIP anexado** como arquivo único: precisa receber a estrutura de pastas acima.
- `app-debug.apk` é um APK **de teste**, assinado automaticamente pela configuração de debug no runner. Não use como APK de publicação na Play Store.
- O modelo Vosk de português não é incluído dentro do APK: ele é baixado uma vez pelo app ao iniciar, com sua autorização.
- O código ainda não foi compilado e testado neste ambiente; o workflow pode revelar algum erro que precise de ajuste.
