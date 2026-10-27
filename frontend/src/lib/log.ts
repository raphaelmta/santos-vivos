/** Logs do terminal do frontend (servidor Next.js), com timestamp local e nível, no mesmo estilo do backend. */
const ts = () => {
  const d = new Date();
  const p = (n: number, w = 2) => String(n).padStart(w, "0");
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
};
export const log = {
  info: (msg: string) => console.log(`${ts()} INFO  [frontend] ${msg}`),
  warn: (msg: string) => console.warn(`${ts()} WARN  [frontend] ${msg}`),
  error: (msg: string) => console.error(`${ts()} ERROR [frontend] ${msg}`),
};
