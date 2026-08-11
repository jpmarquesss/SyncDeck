# Vetores de protocolo

<code>protocol-vector.json</code> contém valores determinísticos para confirmar que Android e Windows constroem exatamente os mesmos hashes e assinaturas.

O campo <code>secretHex</code> é público e existe apenas para teste. Não é uma credencial, não foi gerado por um dispositivo e não deve ser usado em produção.

<code>ProtocolVectorTest.java</code> valida a implementação Java. <code>scripts/validate_repository.py</code> valida o mesmo vetor somente com a biblioteca padrão do Python, permitindo execução no CI sem Android SDK.
