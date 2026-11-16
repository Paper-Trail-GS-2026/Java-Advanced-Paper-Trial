#!/usr/bin/env bash
set -euo pipefail

: "${JWT_PUBLIC_KEY_PEM_B64:?Configure JWT_PUBLIC_KEY_PEM_B64 in App Service.}"
: "${JWT_PRIVATE_KEY_PEM_B64:?Configure JWT_PRIVATE_KEY_PEM_B64 in App Service.}"
umask 077
key_dir=/home/papertrail-keys
mkdir -p "$key_dir"
printf '%s' "$JWT_PUBLIC_KEY_PEM_B64" | base64 --decode > "$key_dir/public.pem"
printf '%s' "$JWT_PRIVATE_KEY_PEM_B64" | base64 --decode > "$key_dir/private.pem"
export JWT_PUBLIC_KEY="file:$key_dir/public.pem"
export JWT_PRIVATE_KEY="file:$key_dir/private.pem"
unset JWT_PUBLIC_KEY_PEM_B64 JWT_PRIVATE_KEY_PEM_B64

exec java -jar /home/site/wwwroot/app.jar --server.port="${PORT:-80}"
