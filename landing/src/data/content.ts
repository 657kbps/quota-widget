import type { SupportedLocale } from '@/lib/i18n';

export const REPO_URL = 'https://github.com/657kbps/quota-widget';
export const RELEASES_URL = 'https://github.com/657kbps/quota-widget/releases/latest';

export interface ProviderItem {
  id: string;
  name: Record<SupportedLocale, string>;
  icon: string;
  monochrome: boolean;
  summary: Record<SupportedLocale, string>;
}

const sameName = (name: string): Record<SupportedLocale, string> => ({
  en: name,
  'zh-Hans': name,
  'zh-Hant': name,
  ja: name,
  de: name,
  fr: name,
  ru: name,
});

export const PROVIDERS: ProviderItem[] = [
  {
    id: 'antigravity',
    name: sameName('Antigravity'),
    icon: 'images/providers/ic_provider_antigravity.png',
    monochrome: false,
    summary: {
      en: '5h · Weekly (Unofficial)',
      'zh-Hans': '5h · 周配额（非官方）',
      'zh-Hant': '5h · 週配額（非官方）',
      ja: '5時間 · 週間（非公式）',
      de: '5h · Woche (Inoffiziell)',
      fr: '5h · Hebdo (Non officiel)',
      ru: '5ч · Неделя (Неофиц.)',
    },
  },
  {
    id: 'cline',
    name: sameName('Cline Pass'),
    icon: 'images/providers/ic_provider_cline.png',
    monochrome: true,
    summary: {
      en: '5h · Weekly · Monthly',
      'zh-Hans': '5h · 周 · 月',
      'zh-Hant': '5h · 週 · 月',
      ja: '5時間 · 週間 · 月間',
      de: '5h · Woche · Monat',
      fr: '5h · Hebdo · Mensuel',
      ru: '5ч · Неделя · Месяц',
    },
  },
  {
    id: 'codex',
    name: sameName('Codex'),
    icon: 'images/providers/ic_provider_codex.png',
    monochrome: false,
    summary: {
      en: '5h · Weekly · Monthly',
      'zh-Hans': '5h · 周 · 月',
      'zh-Hant': '5h · 週 · 月',
      ja: '5時間 · 週間 · 月間',
      de: '5h · Woche · Monat',
      fr: '5h · Hebdo · Mensuel',
      ru: '5ч · Неделя · Месяц',
    },
  },
  {
    id: 'command_code',
    name: sameName('Command Code'),
    icon: 'images/providers/ic_provider_commandcode.png',
    monochrome: true,
    summary: {
      en: 'Limits · Credits',
      'zh-Hans': '限额 · 月度积分',
      'zh-Hant': '限額 · 每月積分',
      ja: '上限 · クレジット',
      de: 'Limits · Guthaben',
      fr: 'Limites · Crédits',
      ru: 'Лимиты · Кредиты',
    },
  },
  {
    id: 'deepseek',
    name: sameName('DeepSeek'),
    icon: 'images/providers/ic_provider_deepseek.png',
    monochrome: false,
    summary: {
      en: 'Balance · CNY / USD',
      'zh-Hans': '账户余额 · CNY / USD',
      'zh-Hant': '帳戶餘額 · CNY / USD',
      ja: '残高 · CNY / USD',
      de: 'Guthaben · CNY / USD',
      fr: 'Solde · CNY / USD',
      ru: 'Баланс · CNY / USD',
    },
  },
  {
    id: 'kimi_code',
    name: sameName('Kimi Code'),
    icon: 'images/providers/ic_provider_kimi.png',
    monochrome: true,
    summary: {
      en: '5h · Weekly · Monthly',
      'zh-Hans': '5h · 周 · 月',
      'zh-Hant': '5h · 週 · 月',
      ja: '5時間 · 週間 · 月間',
      de: '5h · Woche · Monat',
      fr: '5h · Hebdo · Mensuel',
      ru: '5ч · Неделя · Месяц',
    },
  },
  {
    id: 'magpie',
    name: sameName('Magpie'),
    icon: 'images/providers/ic_provider_magpie.png',
    monochrome: true,
    summary: {
      en: 'Gateway Aggregation',
      'zh-Hans': '网关多账号汇总',
      'zh-Hant': '閘道多帳號彙總',
      ja: 'ゲートウェイ集約',
      de: 'Gateway-Aggregation',
      fr: 'Agrégation passerelle',
      ru: 'Агрегация шлюза',
    },
  },
  {
    id: 'new_api',
    name: sameName('NewAPI'),
    icon: 'images/providers/ic_provider_newapi.png',
    monochrome: false,
    summary: {
      en: 'Balance · Usage %',
      'zh-Hans': '余额 · 用量百分比',
      'zh-Hant': '餘額 · 用量百分比',
      ja: '残高 · 使用率 %',
      de: 'Guthaben · Nutzung %',
      fr: 'Solde · Utilisation %',
      ru: 'Баланс · Расход %',
    },
  },
  {
    id: 'opencode_go',
    name: sameName('OpenCode Go'),
    icon: 'images/providers/ic_provider_opencode.png',
    monochrome: true,
    summary: {
      en: 'Rolling · Weekly · Monthly',
      'zh-Hans': '滚动 · 周 · 月',
      'zh-Hant': '滾動 · 週 · 月',
      ja: 'ローリング · 週間 · 月間',
      de: 'Rollierend · Woche · Monat',
      fr: 'Glissant · Hebdo · Mensuel',
      ru: 'Скольз. · Неделя · Месяц',
    },
  },
  {
    id: 'workbuddy',
    name: sameName('WorkBuddy'),
    icon: 'images/providers/ic_provider_workbuddy.png',
    monochrome: false,
    summary: {
      en: 'Monthly Credits',
      'zh-Hans': '月度积分',
      'zh-Hant': '每月積分',
      ja: '月間クレジット',
      de: 'Monatliches Guthaben',
      fr: 'Crédits mensuels',
      ru: 'Месячные кредиты',
    },
  },
  {
    id: 'workbuddy_ai',
    name: {
      en: 'WorkBuddy International',
      'zh-Hans': 'WorkBuddy 国际版',
      'zh-Hant': 'WorkBuddy 國際版',
      ja: 'WorkBuddy 国際版',
      de: 'WorkBuddy International',
      fr: 'WorkBuddy International',
      ru: 'WorkBuddy International',
    },
    icon: 'images/providers/ic_provider_workbuddy.png',
    monochrome: false,
    summary: {
      en: 'Monthly Credits',
      'zh-Hans': '月度积分',
      'zh-Hant': '每月積分',
      ja: '月間クレジット',
      de: 'Monatliches Guthaben',
      fr: 'Crédits mensuels',
      ru: 'Месячные кредиты',
    },
  },
  {
    id: 'zhipu',
    name: sameName('Zhipu Coding Plan'),
    icon: 'images/providers/ic_provider_zhipu.png',
    monochrome: false,
    summary: {
      en: '5h · Weekly',
      'zh-Hans': '5h · 周限额',
      'zh-Hant': '5h · 週限額',
      ja: '5時間 · 週間',
      de: '5h · Woche',
      fr: '5h · Hebdo',
      ru: '5ч · Неделя',
    },
  },
];

export interface LocaleCopy {
  pageTitle: string;
  appName: string;
  systemLanguage: string;
  hero: {
    headline: string;
    subhead: string;
    download: string;
    github: string;
  };
  widgets: {
    used: string;
    remaining: string;
    bar: string;
    capsule: string;
    dark: string;
    light: string;
    resetsAt: string;
    resetsOn: string;
    updatedNow: string;
    updatedAt: string;
    rolling: string;
    weekly: string;
  };
  showcase: {
    title: string;
    tabs: ReadonlyArray<{
      id: 'dark' | 'home' | 'light';
      label: string;
      src: string;
    }>;
  };
  providers: {
    title: string;
  };
  essentials: ReadonlyArray<{
    id: 'refresh' | 'security' | 'theme';
    title: string;
    desc: string;
  }>;
}

export const COPY: Record<SupportedLocale, LocaleCopy> = {
  en: {
    pageTitle: 'Quota Widget — AI quotas, on your home screen',
    appName: 'Quota Widget',
    systemLanguage: 'Follow system',
    hero: {
      headline: 'Quotas, right on your home screen.',
      subhead: 'A simple, handy quota widget.',
      download: 'Download APK',
      github: 'Source',
    },
    widgets: {
      used: 'Used',
      remaining: 'Left',
      bar: 'Bar',
      capsule: 'Capsule',
      dark: 'Dark',
      light: 'Light',
      resetsAt: 'Resets at 06:15',
      resetsOn: 'Resets on Nov 30 at 03:03',
      updatedNow: 'Updated just now',
      updatedAt: 'Updated Nov 25, 03:03',
      rolling: 'Rolling',
      weekly: 'Weekly',
    },
    showcase: {
      title: 'Right on your home screen',
      tabs: [
        { id: 'dark', label: 'Dark Widgets', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: 'App Home', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: 'Light Widgets', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '12 providers supported',
    },
    essentials: [
      {
        id: 'refresh',
        title: 'Background sync',
        desc: '15m to 24h intervals, plus one-tap refresh on any widget',
      },
      {
        id: 'security',
        title: 'Encrypted locally',
        desc: 'API keys and tokens protected with Google Tink',
      },
      {
        id: 'theme',
        title: 'Material You',
        desc: 'Dynamic wallpaper colors, dark mode, and 7 languages',
      },
    ],
  },
  'zh-Hans': {
    pageTitle: '配额监控 · Quota Widget — 额度，桌面即见',
    appName: '配额监控',
    systemLanguage: '跟随系统',
    hero: {
      headline: '额度，桌面即见。',
      subhead: '一个简单好用的额度小组件。',
      download: '下载 APK',
      github: '源码',
    },
    widgets: {
      used: '已用',
      remaining: '余量',
      bar: '细条',
      capsule: '胶囊',
      dark: '深色',
      light: '浅色',
      resetsAt: '06:15 重置',
      resetsOn: '11月30日 03:03 重置',
      updatedNow: '刚刚更新',
      updatedAt: '11月25日 03:03 更新',
      rolling: '5 小时',
      weekly: '每周',
    },
    showcase: {
      title: '桌面即看板',
      tabs: [
        { id: 'dark', label: '深色小组件', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: '平台配置', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: '浅色小组件', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '支持 12 个平台',
    },
    essentials: [
      {
        id: 'refresh',
        title: '自动刷新',
        desc: '15 分钟至 24 小时可调，点按小组件即可立即同步',
      },
      {
        id: 'security',
        title: '本地加密',
        desc: 'API Key 与会话令牌通过 Google Tink 加密保存',
      },
      {
        id: 'theme',
        title: '随系统而变',
        desc: 'Material You 动态取色，内置 7 种语言',
      },
    ],
  },
  'zh-Hant': {
    pageTitle: '配額監控 · Quota Widget — 額度，桌面即見',
    appName: '配額監控',
    systemLanguage: '跟隨系統',
    hero: {
      headline: '額度，桌面即見。',
      subhead: '一個簡單好用的額度小工具。',
      download: '下載 APK',
      github: '原始碼',
    },
    widgets: {
      used: '已用',
      remaining: '剩餘',
      bar: '細條',
      capsule: '膠囊',
      dark: '深色',
      light: '淺色',
      resetsAt: '06:15 重置',
      resetsOn: '11月30日 03:03 重置',
      updatedNow: '剛剛更新',
      updatedAt: '11月25日 03:03 更新',
      rolling: '5 小時',
      weekly: '每週',
    },
    showcase: {
      title: '桌面即看板',
      tabs: [
        { id: 'dark', label: '深色小工具', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: '平台設定', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: '淺色小工具', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '支援 12 個平台',
    },
    essentials: [
      {
        id: 'refresh',
        title: '自動重新整理',
        desc: '15 分鐘至 24 小時可調，點擊小工具即可立即同步',
      },
      {
        id: 'security',
        title: '本機加密',
        desc: 'API Key 與工作階段權杖經 Google Tink 加密儲存',
      },
      {
        id: 'theme',
        title: '隨系統切換',
        desc: 'Material You 動態取色，內建 7 種語言',
      },
    ],
  },
  ja: {
    pageTitle: 'クォータウィジェット — クォータをホーム画面に',
    appName: 'クォータウィジェット',
    systemLanguage: 'システムに従う',
    hero: {
      headline: 'クォータを、ホーム画面で。',
      subhead: 'シンプルで使いやすいクォータウィジェット。',
      download: 'APK をダウンロード',
      github: 'ソース',
    },
    widgets: {
      used: '使用済み',
      remaining: '残り',
      bar: 'バー',
      capsule: 'カプセル',
      dark: 'ダーク',
      light: 'ライト',
      resetsAt: '06:15 にリセット',
      resetsOn: '11月30日 03:03 にリセット',
      updatedNow: 'たった今更新',
      updatedAt: '11月25日 03:03 に更新',
      rolling: '5時間',
      weekly: '週間',
    },
    showcase: {
      title: 'ホーム画面でひと目で把握',
      tabs: [
        { id: 'dark', label: 'ダークウィジェット', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: 'アプリ画面', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: 'ライトウィジェット', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '12 のプラットフォームに対応',
    },
    essentials: [
      {
        id: 'refresh',
        title: 'バックグラウンド更新',
        desc: '15分〜24時間間隔で自動更新、タップで即時同期',
      },
      {
        id: 'security',
        title: 'ローカル暗号化',
        desc: 'API キーとトークンを Google Tink で暗号化保存',
      },
      {
        id: 'theme',
        title: 'Material You',
        desc: 'ダイナミックカラー・ダークモード・7言語対応',
      },
    ],
  },
  de: {
    pageTitle: 'Quota Widget — Kontingente direkt auf dem Startbildschirm',
    appName: 'Quota Widget',
    systemLanguage: 'Systemeinstellung folgen',
    hero: {
      headline: 'Kontingente, direkt im Blick.',
      subhead: 'Ein schlichtes, praktisches Kontingent-Widget.',
      download: 'APK herunterladen',
      github: 'Quellcode',
    },
    widgets: {
      used: 'Genutzt',
      remaining: 'Übrig',
      bar: 'Leiste',
      capsule: 'Kapsel',
      dark: 'Dunkel',
      light: 'Hell',
      resetsAt: 'Zurücksetzung um 06:15',
      resetsOn: 'Zurücksetzung 30. Nov., 03:03',
      updatedNow: 'Gerade aktualisiert',
      updatedAt: 'Aktualisiert 25. Nov., 03:03',
      rolling: 'Rollierend',
      weekly: 'Woche',
    },
    showcase: {
      title: 'Direkt auf deinem Startbildschirm',
      tabs: [
        { id: 'dark', label: 'Dunkle Widgets', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: 'App-Startseite', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: 'Helle Widgets', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '12 Anbieter unterstützt',
    },
    essentials: [
      {
        id: 'refresh',
        title: 'Hintergrund-Sync',
        desc: 'Intervalle von 15 Min. bis 24 Std. und Tippen zum Aktualisieren',
      },
      {
        id: 'security',
        title: 'Lokal verschlüsselt',
        desc: 'API-Schlüssel und Token geschützt mit Google Tink',
      },
      {
        id: 'theme',
        title: 'Material You',
        desc: 'Dynamische Farben, Dunkelmodus und 7 Sprachen',
      },
    ],
  },
  fr: {
    pageTitle: 'Quota Widget — Vos quotas sur l’écran d’accueil',
    appName: 'Quota Widget',
    systemLanguage: 'Suivre le système',
    hero: {
      headline: 'Vos quotas, dès l’écran d’accueil.',
      subhead: 'Un widget de quotas simple et pratique.',
      download: 'Télécharger l’APK',
      github: 'Source',
    },
    widgets: {
      used: 'Utilisé',
      remaining: 'Restant',
      bar: 'Barre',
      capsule: 'Capsule',
      dark: 'Sombre',
      light: 'Clair',
      resetsAt: 'Réinitialisation à 06:15',
      resetsOn: 'Réinitialisation le 30 nov. à 03:03',
      updatedNow: 'Actualisé à l’instant',
      updatedAt: 'Actualisé le 25 nov. à 03:03',
      rolling: 'Glissant',
      weekly: 'Hebdo',
    },
    showcase: {
      title: 'Directement sur votre écran',
      tabs: [
        { id: 'dark', label: 'Widgets sombres', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: 'Accueil', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: 'Widgets clairs', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: '12 plateformes prises en charge',
    },
    essentials: [
      {
        id: 'refresh',
        title: 'Synchro en arrière-plan',
        desc: 'Intervalles de 15 min à 24 h et actualisation en un geste',
      },
      {
        id: 'security',
        title: 'Chiffrement local',
        desc: 'Clés API et jetons protégés par Google Tink',
      },
      {
        id: 'theme',
        title: 'Material You',
        desc: 'Couleurs dynamiques, mode sombre et 7 langues',
      },
    ],
  },
  ru: {
    pageTitle: 'Quota Widget — Квоты на главном экране',
    appName: 'Quota Widget',
    systemLanguage: 'Как в системе',
    hero: {
      headline: 'Квоты — прямо на рабочем столе.',
      subhead: 'Простой и удобный виджет квот.',
      download: 'Скачать APK',
      github: 'Код',
    },
    widgets: {
      used: 'Использовано',
      remaining: 'Осталось',
      bar: 'Полоса',
      capsule: 'Капсула',
      dark: 'Тёмная',
      light: 'Светлая',
      resetsAt: 'Сброс в 06:15',
      resetsOn: 'Сброс 30 нояб. в 03:03',
      updatedNow: 'Обновлено только что',
      updatedAt: 'Обновлено 25 нояб., 03:03',
      rolling: '5 часов',
      weekly: 'Неделя',
    },
    showcase: {
      title: 'Прямо на главном экране',
      tabs: [
        { id: 'dark', label: 'Тёмные виджеты', src: 'images/screenshots/widgets-dark.png' },
        { id: 'home', label: 'Главный экран', src: 'images/screenshots/app-home.png' },
        { id: 'light', label: 'Светлые виджеты', src: 'images/screenshots/widgets-light.png' },
      ],
    },
    providers: {
      title: 'Поддержка 12 платформ',
    },
    essentials: [
      {
        id: 'refresh',
        title: 'Фоновое обновление',
        desc: 'Интервал от 15 мин до 24 ч и обновление по нажатию',
      },
      {
        id: 'security',
        title: 'Локальное шифрование',
        desc: 'Ключи API и токены защищены Google Tink',
      },
      {
        id: 'theme',
        title: 'Material You',
        desc: 'Динамические цвета, тёмная тема и 7 языков',
      },
    ],
  },
};
