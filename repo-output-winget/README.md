# Static WinGet output

## usage

generate keys:

```shell
./gradlew :repo-output-winget:wingetCert
```

generate repo:

```shell
./gradlew :repo-cli:run --args="\
  repo \
    -t winget \
    --repo file:${PWD}/build/winget \
    --published-base https://${host}/winget/ \
    -P ${PWD}/build/simple-repo-winget.cer \
    -S ${PWD}/build/simple-repo-winget.pfx \
    add \
    --init=init \
    -c ${PWD}/repo-output-winget/src/test/resources/config.yaml\
"

ssh ${host} mkdir -p /var/www/${host}/winget/
rsync -avz --delete build/winget/. ${host}:/var/www/${host}/winget/.
```

add repo:

```shell
powershell.exe -Command "Import-Certificate -CertStoreLocation 'Cert:\LocalMachine\TrustedPeople' -FilePath 'simple-repo-winget.cer'"
winget source add \
  --name simple-repo \
  --arg https://${host}.com/winget/ \
  --type Microsoft.PreIndexed.Package \
  --trust-level trusted \
  --accept-source-agreements
winget install --source simple-repo --exact --id Example.Package
```
