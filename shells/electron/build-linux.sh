docker run --rm \
	--env-file < $(env) \
	-v ${PWD}:/project \
	-v ~/.cache/electron:/root/.cache/electron \
	-v ~/.cache/electron-builder:/root/.cache/electron-builder \
	electronuserland/builder:wine \
	/bin/bash -c "yarn --link-duplicates --pure-lockfile && yarn release --linux --win"
