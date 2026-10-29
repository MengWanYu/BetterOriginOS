#!/system/bin/sh
cmd notification post -t '点击测试' -c 'activity -a android.settings.SETTINGS' t_click '点击这张卡应打开设置'
cmd notification post -t '同应用 A' t_a 'A 正文，用于验证独立卡片'
cmd notification post -t '同应用 B' t_b 'B 正文，用于验证独立卡片'
cmd notification post -t '同应用长文' -S bigtext t_long '这是一条用于验证多行正文、独立卡片与滑动删除的长通知。第二行继续补充文字，第三行继续补充文字，第四行继续补充文字。'
