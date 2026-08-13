# Vetores de protocolo

<code>protocol-vector.json</code> contém valores determinísticos para confirmar hashes, assinaturas e a criptografia de conteúdo do protocolo v2.

O campo <code>secretHex</code> é público e existe apenas para teste. Não é uma credencial, não foi gerado por um dispositivo e não deve ser usado em produção.

<code>ProtocolVectorTest.java</code> é independente do Android e valida HMAC, AES-256-CBC e a ordem cifrar-e-autenticar. <code>ProtocolVectorTest.swift</code> mantém a validação das assinaturas compatíveis com o cliente iOS. <code>scripts/validate_repository.py</code> valida os campos determinísticos usados pelo CI.
