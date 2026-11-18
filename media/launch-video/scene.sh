#!/usr/bin/env bash
# scene.sh NAME START END  → renders frames [START,END) at 30 fps into NAME.mp4
set -euo pipefail
NAME=$1; START=$2; END=$3
DIR=frames_$NAME; mkdir -p "$DIR"
TIMES=$(python3 -c "s,e=$START,$END; n=round((e-s)*30); print(' '.join(f'{s+i/30:.4f}' for i in range(n)))")
python3 - "$DIR" $TIMES <<'PY'
import asyncio, sys
from playwright.async_api import async_playwright
d=sys.argv[1]; ts=[float(x) for x in sys.argv[2:]]
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch(); pg=await b.new_page(viewport={"width":1920,"height":1080})
        await pg.goto("file:///home/claude/video/launch.html"); await pg.wait_for_timeout(500)
        for i,t in enumerate(ts):
            await pg.evaluate(f"render({t})")
            await pg.screenshot(path=f"{d}/f{i:05d}.jpg", type="jpeg", quality=92)
        await b.close()
asyncio.run(main())
PY
ffmpeg -y -loglevel error -framerate 30 -i "$DIR/f%05d.jpg" -c:v libx264 -crf 18 -preset veryfast -pix_fmt yuv420p "tmp_$NAME.mp4" && mv "tmp_$NAME.mp4" "$NAME.mp4"
echo "$NAME done"
