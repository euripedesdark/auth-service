import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ptBR from './locales/pt-BR.json';
import enUS from './locales/en-US.json';
import esES from './locales/es-ES.json';
import frFR from './locales/fr-FR.json';

i18n.use(initReactI18next).init({
  resources: { 'pt-BR': { translation: ptBR }, 'en-US': { translation: enUS }, 'es-ES': { translation: esES }, 'fr-FR': { translation: frFR } },
  lng: 'pt-BR',
  fallbackLng: 'pt-BR',
  interpolation: { escapeValue: false }
});

export default i18n;
