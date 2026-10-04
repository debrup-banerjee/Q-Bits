// Fails when src/schema.ts was not regenerated after openapi.json changed.
// Regenerates into a temp file with the same options as `npm run generate` and compares.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const dir = mkdtempSync(join(tmpdir(), 'qbits-schema-'));
const fresh = join(dir, 'schema.ts');
try {
  execFileSync(
    'npx',
    ['openapi-typescript', 'openapi.json', '-o', fresh, '--properties-required-by-default'],
    { stdio: ['ignore', 'ignore', 'inherit'] },
  );
  if (readFileSync(fresh, 'utf8') !== readFileSync('src/schema.ts', 'utf8')) {
    console.error(
      'src/schema.ts does not match openapi.json. Run `npm run generate:api` and commit the result.',
    );
    process.exitCode = 1;
  } else {
    console.log('src/schema.ts matches openapi.json.');
  }
} finally {
  rmSync(dir, { recursive: true, force: true });
}
