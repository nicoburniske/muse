nixpacks build . --build-cmd "sbt stage -mem 4096" --name muse_server --platform linux/amd64 --cache-key "muse_server_1"
#flyctl deploy --image muse_server --local-only --env NIXPACKS_BUILD_CMD="sbt stage -mem 4096"

#flyctl deploy --nixpacks --env NIXPACKS_BUILD_CMD="sbt stage -mem 4096" --local-only

flyctl deploy --local-only --nixpacks --env NIXPACKS_BUILD_CMD="sbt stage -mem 4096"