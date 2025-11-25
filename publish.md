# Publishing plugin

This document describes the steps required to prepare, build and publish the IntelliJ plugin.

## Create (or confirm) Access to the JetBrains Marketplace
We need a JetBrains account that will act as the owner of the plugin.
1. Login in to [Jetbranins](https://plugins.jetbrains.com/)
2. Create an organization

# Register the Plugin in the Marketplace
Before we upload anything, the plugin must exist as an entry in the Marketplace.
1. Go to **My Plugins**
2. Click **Add New Plugin**
3. Chose **Upload manually** (will be replaced with the CI uploads)
4. Upload any ZIP file or live unlisted (this is only to create the plugin entry)
5. Set the **Plugin ID** to **xygeni** or whatever you want (I need to know this value)


# Generate the Publishing Token
we need a **Marketplace Token** to upload the plugin.
1. Go to **My Profile -> Marketplace Tokens**
2. Click **Generate New Token** (permissions: _Upload Plugin_)
3. Copy the token and store it securely in the GitHub repository secrets (PUBLISH_TOKEN)

## Prepare the Certificate for Plugin Signing
[Reference](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate#generate-private-key)

To generate an RSA private.pem private key, run the `openssl genpkey` command in the terminal, as below:
```
openssl genpkey\
  -aes-256-cbc\
  -algorithm RSA\
  -out private_encrypted.pem\
  -pkeyopt rsa_keygen_bits:4096
```
Convert it into the RSA form with:
```
openssl rsa\
  -in private_encrypted.pem\
  -out private.pem
```
Generate a **chain.crt** certificate chain with:
```
openssl req\
  -key private.pem\
  -new\
  -x509\
  -days 365\
  -out chain.crt
```

Store them securely in the GitHub repository secrets (PRIVATE_KEY, PRIVATE_KEY_PASSWORD, CERTIFICATE_CHAIN)

 ## Environment Variables
| Environment variable   | Description                                                                                                  |
|------------------------|--------------------------------------------------------------------------------------------------------------|
| `PRIVATE_KEY`          | Certificate private key, should contain: `-----BEGIN RSA PRIVATE KEY----- ... -----END RSA PRIVATE KEY-----` |
| `PRIVATE_KEY_PASSWORD` | Password used for encrypting the certificate file.                                                           |
| `CERTIFICATE_CHAIN`    | Certificate chain, should contain: `-----BEGIN CERTIFICATE----- ... -----END CERTIFICATE----`                |
| `PUBLISH_TOKEN`        | Publishing token generated in your JetBrains Marketplace profile dashboard.                                  |