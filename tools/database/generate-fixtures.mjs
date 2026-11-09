import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';

const root = new URL('./', import.meta.url);
const dataset = JSON.parse(readFileSync(new URL('fixtures.json', root), 'utf8'));
const checkOnly = process.argv.slice(2);
assert(checkOnly.length === 0 || (checkOnly.length === 1 && checkOnly[0] === '--check'), 'Uso: node tools/database/generate-fixtures.mjs [--check]');

function ids(rows, label) {
  const result = new Set();
  for (const row of rows) {
    assert(Number.isSafeInteger(row.id) && row.id > 0 && row.id < 1000, `${label}: ID de fixture deve estar entre 1 e 999`);
    assert(!result.has(row.id), `${label}: ID duplicado ${row.id}`);
    result.add(row.id);
  }
  return result;
}

function text(value, maxLength, label) {
  assert(typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength, `${label}: texto obrigatório, máximo ${maxLength} caracteres`);
}

assert.equal(dataset.fictitious, true, 'Não publicar fontes fictícias como corpus oficial');
assert.equal(dataset.services.length, 3);
assert.equal(dataset.preparations.length, 7);
assert(!Number.isNaN(Date.parse(dataset.createdAt)));
const categoryIds = ids(dataset.categories, 'Categoria');
for (const category of dataset.categories) text(category.name, 100, 'Nome da categoria');
const serviceIds = ids(dataset.services, 'Serviço');
ids(dataset.preparations, 'Preparativo');
ids(dataset.services.flatMap(service => service.requirements), 'Requisito');
ids(dataset.preparations.flatMap(preparation => preparation.items), 'Item');

for (const service of dataset.services) {
  assert(categoryIds.has(service.categoryId), `Serviço ${service.id}: categoria inexistente`);
  assert(Number.isSafeInteger(service.version) && service.version > 0);
  text(service.title, 160, 'Título do serviço');
  text(service.description, 2000, 'Descrição do serviço');
  text(service.city, 120, 'Município');
  for (const requirement of service.requirements) {
    assert.equal(typeof requirement.required, 'boolean');
    text(requirement.title, 200, 'Título do requisito');
    text(requirement.sourceUrl, 2048, 'URL do requisito');
    assert.equal(new URL(requirement.sourceUrl).hostname, 'example.invalid');
  }
}

for (const preparation of dataset.preparations) {
  assert(serviceIds.has(preparation.serviceId), `Preparativo ${preparation.id}: serviço inexistente`);
  const service = dataset.services.find(row => row.id === preparation.serviceId);
  const requirementIds = new Set(service.requirements.map(row => row.id));
  assert(Number.isSafeInteger(preparation.sourceVersion) && preparation.sourceVersion > 0 && preparation.sourceVersion <= service.version);
  text(preparation.title, 160, 'Título do preparativo');
  text(preparation.ownerSubject, 128, 'Subject');
  for (const item of preparation.items) {
    assert(item.requirementId === null || requirementIds.has(item.requirementId), `Item ${item.id}: requisito de outro serviço`);
    assert.equal(typeof item.required, 'boolean');
    assert.equal(typeof item.completed, 'boolean');
    text(item.title, 200, 'Título do item');
  }
  const required = preparation.items.filter(item => item.required);
  const pending = required.filter(item => !item.completed).length;
  assert.deepEqual(preparation.expectedProgress, {
    totalItems: preparation.items.length,
    requiredItems: required.length,
    pendingRequiredItems: pending,
    completionPercent: required.length ? 100 * (required.length - pending) / required.length : 0,
    ready: required.length > 0 && pending === 0,
    sourceOutdated: preparation.sourceVersion < service.version,
  }, `Progresso esperado do preparativo ${preparation.id}`);
}

// Asserções independentes fixam os casos de aceite, inclusive opcionais e vazio.
const byId = id => dataset.preparations.find(preparation => preparation.id === id);
assert.equal(byId(101).expectedProgress.completionPercent, 0);
assert.equal(byId(102).expectedProgress.completionPercent, 50);
assert.equal(byId(103).expectedProgress.ready, true);
assert(byId(103).items.some(item => !item.required && !item.completed));
assert.equal(byId(106).expectedProgress.ready, false);
assert.equal(byId(107).items.length, 0);
assert.equal(byId(107).expectedProgress.ready, false);
assert.deepEqual(dataset.preparations.filter(preparation => preparation.expectedProgress.sourceOutdated).map(row => row.id), [101, 102]);

const sql = value => value === null ? 'NULL' : typeof value === 'string' ? `'${value.replaceAll("'", "''")}'` : typeof value === 'boolean' ? String(Number(value)) : String(value);
const timestamp = `TO_TIMESTAMP_TZ('${dataset.createdAt.replace('Z', '+00:00')}', 'YYYY-MM-DD"T"HH24:MI:SSTZH:TZM')`;
const insert = (table, columns, values) => `INSERT INTO ${table} (${columns}) VALUES (${values.map(sql).join(', ')});`;
const header = `-- Gerado de tools/database/fixtures.json (${dataset.version}).\n`;
const catalog = [];
for (const category of dataset.categories) {
  catalog.push(insert('PT_CATEGORIA', 'ID, NOME', [category.id, category.name]));
}
for (const service of dataset.services) {
  catalog.push(insert('PT_SERVICO', 'ID, CATEGORIA_ID, TITULO, DESCRICAO, MUNICIPIO, VERSAO', [service.id, service.categoryId, service.title, service.description, service.city, service.version]));
  for (const requirement of service.requirements) {
    catalog.push(insert('PT_REQUISITO', 'ID, SERVICO_ID, TITULO, OBRIGATORIO, FONTE_URL', [requirement.id, service.id, requirement.title, requirement.required, requirement.sourceUrl]));
  }
}

const preparations = [];
for (const preparation of dataset.preparations) {
  preparations.push(`INSERT INTO PT_PREPARATIVO (ID, OWNER_SUBJECT, SERVICO_ID, VERSAO_ORIGEM, TITULO, CRIADO_EM) VALUES (${[preparation.id, preparation.ownerSubject, preparation.serviceId, preparation.sourceVersion, preparation.title].map(sql).join(', ')}, ${timestamp});`);
  for (const item of preparation.items) {
    preparations.push(insert('PT_ITEM_PREPARATIVO', 'ID, PREPARATIVO_ID, REQUISITO_ID, TITULO, OBRIGATORIO, CONCLUIDO', [item.id, preparation.id, item.requirementId, item.title, item.required, item.completed]));
  }
}
for (const service of dataset.services) {
  preparations.push(`INSERT INTO PT_VERSAO_SERVICO (SERVICO_ID, VERSAO, ATUALIZADO_EM) VALUES (${service.id}, ${service.version}, ${timestamp});`);
}

const target = new URL('../../docs/database/papertrail.sql', root);
const original = readFileSync(target, 'utf8');
const begin = '-- BEGIN GENERATED FIXTURES\n';
const end = '-- END GENERATED FIXTURES';
const sections = original.split(begin);
assert.equal(sections.length, 2, 'Esperado um marcador de início dos fixtures');
const remainder = sections[1].split(end);
assert.equal(remainder.length, 2, 'Esperado um marcador de fim dos fixtures');
const content = sections[0] + begin + header + catalog.join('\n') + '\n' + preparations.join('\n') + '\n' + end + remainder[1];
if (checkOnly.length) assert.equal(original, content, 'papertrail.sql divergiu dos fixtures; execute o gerador');
else writeFileSync(target, content, 'utf8');
console.log(`${checkOnly.length ? 'Verificação' : 'Geração'} OK: 3 serviços, 7 preparativos, 14 itens; regras de progresso e integridade dos fixtures conferidas.`);
