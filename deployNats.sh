#!/bin/bash
fly deploy -a muse-hub -i nats:latest -c fly-nats.toml