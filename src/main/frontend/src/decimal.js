const DECIMAL_PATTERN = /^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$/;

export function normalize(raw) {
  return String(raw ?? '').trim().replace(',', '.').replace(/\s/g, '');
}

export function validateDecimal(raw, label, { required = true } = {}) {
  const value = normalize(raw);
  if (!value) {
    return required ? `${label}: значение не заполнено` : null;
  }
  if (!DECIMAL_PATTERN.test(value)) {
    return `${label}: «${String(raw).trim()}» не является числом`;
  }
  return null;
}

export function validateInteger(raw, label, options) {
  const base = validateDecimal(raw, label, options);
  if (base) {
    return base;
  }
  const value = normalize(raw);
  if (!value) {
    return null;
  }
  if (!/^[+-]?\d+$/.test(value)) {
    return `${label} должна быть целым числом`;
  }
  return null;
}

export function compareDecimal(raw, limit) {
  const value = normalize(raw);
  const a = splitNumber(value);
  const b = splitNumber(String(limit));
  if (!a || !b) {
    return 0;
  }
  if (a.negative !== b.negative) {
    return a.negative ? -1 : 1;
  }
  const magnitude = compareMagnitude(a, b);
  return a.negative ? -magnitude : magnitude;
}

export function isPositive(raw) {
  const value = normalize(raw);
  if (!value || value.startsWith('-')) {
    return false;
  }
  return /[1-9]/.test(value);
}

function splitNumber(value) {
  const match = /^([+-])?(\d*)(?:\.(\d*))?$/.exec(value);
  if (!match) {
    return null;
  }
  return {
    negative: match[1] === '-',
    int: (match[2] || '0').replace(/^0+(?=\d)/, ''),
    frac: (match[3] || '').replace(/0+$/, ''),
  };
}

function compareMagnitude(a, b) {
  if (a.int.length !== b.int.length) {
    return a.int.length > b.int.length ? 1 : -1;
  }
  if (a.int !== b.int) {
    return a.int > b.int ? 1 : -1;
  }
  const length = Math.max(a.frac.length, b.frac.length);
  const fracA = a.frac.padEnd(length, '0');
  const fracB = b.frac.padEnd(length, '0');
  if (fracA === fracB) {
    return 0;
  }
  return fracA > fracB ? 1 : -1;
}
