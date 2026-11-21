"""Print fresh JWT signing keys as .env lines (run once per environment).

python scripts/gen_keys.py >> .env
"""

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
priv = key.private_bytes(
    serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
).decode()
pub = (
    key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()
)
esc = lambda s: s.strip().replace("\n", "\\n")  # noqa: E731
print(f'JWT_PRIVATE_KEY_PEM="{esc(priv)}"')
print(f'JWT_PUBLIC_KEY_PEM="{esc(pub)}"')
