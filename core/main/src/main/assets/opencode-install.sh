#!/bin/sh
set -e
apk add --no-cache nodejs npm
npm install -g @opencode/cli
export OPENCODE_SERVER_PASSWORD='andlinux-local-opencode'
exec opencode web --port 4123 --hostname 127.0.0.1 || exec opencode serve --port 4123 --hostname 127.0.0.1
