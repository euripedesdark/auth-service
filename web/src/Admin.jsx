import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { Password } from 'primereact/password';
import { Button } from 'primereact/button';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Image } from 'primereact/image';
import { Toast } from 'primereact/toast';
import { Divider } from 'primereact/divider';
import { Message } from 'primereact/message';
import { api, basicOf, setAuth, qrUrl } from './api';

export default function Admin() {
  const { t } = useTranslation();
  const toast = useRef(null);
  const [user, setUser] = useState('');
  const [pass, setPass] = useState('');
  const [dom, setDom] = useState('');
  const [dns, setDns] = useState('');
  const [logged, setLogged] = useState(null);
  const [cUser, setCUser] = useState('');
  const [mUser, setMUser] = useState('');
  const [qr, setQr] = useState(null);
  const [pending, setPending] = useState([]);
  const [rUser, setRUser] = useState('');
  const [revoked, setRevoked] = useState(null);

  async function login() {
    setDns('');
    if (dom.trim()) {
      const rd = await api('/api/v1/identity/resolve-domain?domain=' + encodeURIComponent(dom.trim()));
      if (!rd.ok) { toast.current.show({ severity: 'error', summary: t('login.fail', { status: rd.status }), detail: t('login.dnsFail') }); return; }
      setDns(t('login.dnsOk', { ips: rd.data.ips.join(', ') }));
      const r = await api('/api/v1/identity/authenticate-domain', { method: 'POST', body: { username: user.trim(), password: pass, domain: dom.trim() } });
      if (!r.ok) { toast.current.show({ severity: 'error', summary: t('login.fail', { status: r.status }) }); return; }
      setAuth(basicOf(user.trim(), pass), dom.trim());
      setLogged(t('login.okDomain', { domain: dom.trim(), user: r.data.username }));
    } else {
      setAuth(basicOf(user.trim(), pass), null);
      const r = await api('/api/v1/identity/me');
      if (!r.ok) { setAuth(null, null); toast.current.show({ severity: 'error', summary: t('login.fail', { status: r.status }) }); return; }
      setLogged(t('login.ok', { user: r.data.username }));
    }
    loadPending();
  }

  async function loadPending() {
    const r = await api('/api/v1/certificates/pending');
    if (r.ok) setPending(r.data);
  }

  async function emitir() {
    const r = await api('/api/v1/certificates/issue', { method: 'POST', body: { username: cUser } });
    if (r.ok) {
      toast.current.show({ severity: 'success', summary: t('issue.ok', { id: r.data.id, status: r.data.status }) });
      loadPending();
    } else toast.current.show({ severity: 'error', summary: t('issue.fail') });
  }

  async function genMfa() {
    const r = await api('/api/v1/mfa/setup', { method: 'POST', body: { username: mUser } });
    if (!r.ok) { toast.current.show({ severity: 'error', summary: t('mfa.fail', { status: r.status }) }); return; }
    setQr(r.data);
  }

  async function revogar() {
    const r = await api('/api/v1/certificates/revoke', { method: 'POST', body: { username: rUser } });
    if (r.ok) {
      toast.current.show({ severity: 'warn', summary: t('revoke.ok', { n: r.data.length }) });
      loadPending(); loadRevoked();
    } else toast.current.show({ severity: 'error', summary: 'Erro (' + r.status + ')' });
  }

  async function loadRevoked() {
    const r = await api('/api/v1/certificates/revoked');
    if (r.ok) setRevoked(r.data);
  }

  return (
    <div className="p-3" style={{ maxWidth: 760, margin: '0 auto' }}>
      <Toast ref={toast} />
      <h1>{t('app.adminTitle')}</h1>

      <Card title={t('login.title')} className="mb-3">
        <div className="flex flex-column gap-2">
          <label>{t('login.user')}</label>
          <InputText value={user} onChange={e => setUser(e.target.value)} placeholder={t('login.userPh')} />
          <label>{t('login.password')}</label>
          <Password value={pass} onChange={e => setPass(e.target.value)} feedback={false} toggleMask />
          <label>{t('login.domain')}</label>
          <InputText value={dom} onChange={e => setDom(e.target.value)} placeholder={t('login.domainPh')} />
          <Button label={t('login.button')} icon="pi pi-sign-in" onClick={login} />
          {dns && <Message severity="info" text={dns} />}
          {logged && <Message severity="success" text={logged} />}
        </div>
      </Card>

      {logged && (<>
        <Card title={t('issue.title')} className="mb-3">
          <div className="flex flex-column gap-2">
            <InputText value={cUser} onChange={e => setCUser(e.target.value)} placeholder={t('issue.userPh')} />
            <div><Button label={t('issue.button')} icon="pi pi-file" onClick={emitir} /></div>
          </div>
        </Card>

        <Card title={t('mfa.title')} className="mb-3">
          <div className="flex flex-column gap-2">
            <InputText value={mUser} onChange={e => setMUser(e.target.value)} placeholder={t('mfa.userPh')} />
            <div><Button label={t('mfa.button')} icon="pi pi-qrcode" onClick={genMfa} /></div>
            {qr && (<>
              <Divider />
              <p>{t('mfa.for', { user: qr.username, time: new Date(qr.expiresAt * 1000).toLocaleTimeString() })}</p>
              <Image src={qr.qrDataUrl} alt="QR" width="250" preview />
              <div>
                <a href={qr.qrPngUrl} download={'mfa-' + qr.username + '.png'}><Button label={t('mfa.save')} icon="pi pi-download" className="p-button-outlined" /></a>
              </div>
              <p><b>{t('mfa.secret')}</b> <code>{qr.secret}</code></p>
            </>)}
          </div>
        </Card>

        <Card title={t('pending.title')} className="mb-3">
          <Button label={t('pending.refresh')} icon="pi pi-refresh" className="p-button-text" onClick={loadPending} />
          <DataTable value={pending} emptyMessage={t('pending.empty')} paginator rows={5}>
            <Column field="username" header={t('cols.user')} />
            <Column field="id" header={t('cols.id')} />
            <Column field="status" header={t('cols.status')} />
          </DataTable>
        </Card>

        <Card title={t('revoke.title')} className="mb-3">
          <div className="flex flex-column gap-2">
            <InputText value={rUser} onChange={e => setRUser(e.target.value)} placeholder={t('revoke.userPh')} />
            <div className="flex gap-2">
              <Button label={t('revoke.button')} icon="pi pi-ban" severity="danger" onClick={revogar} />
              <Button label={t('revoke.list')} className="p-button-text" onClick={loadRevoked} />
            </div>
            {revoked && <DataTable value={revoked} emptyMessage={t('revoke.empty')} paginator rows={5}>
              <Column field="username" header={t('cols.user')} />
              <Column field="id" header={t('cols.id')} />
            </DataTable>}
          </div>
        </Card>
      </>)}
    </div>
  );
}
