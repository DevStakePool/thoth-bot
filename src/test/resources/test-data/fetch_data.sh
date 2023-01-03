#!/bin/bash

# KOIOS Calls to gather test data
## Account Addresses
echo "Fetching data for account addresses"
curl -s -X POST "https://api.koios.rest/api/v0/account_addresses" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"]}' | jq > account_addresses.json

## Account Information
echo "Fetching data for account information"
curl -s -X POST "https://api.koios.rest/api/v0/account_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz"]}' | jq > account_information.json

## Address Transactions
### stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32
echo "Fetching data for address transactions stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32"
curl -s -X POST "https://api.koios.rest/api/v0/address_txs" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_addresses":["addr1q9h5zx75k2ugkrc6dxsxgtt4e0ulrshdx3ggw0nqly4n658envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsnjt880","addr1q992ndhkuklhdru6lmkxheegl6903twzn3kdpnfmlt8q6d0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms8xjn0x","addr1q9tkqvtlfuqvqdy36uu5hs37w5sn2x22rvetctq3txslxwhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsvpr66q","addr1qy8snvwxmhvtdp2ymuzdtgvgwnwtn5xuqx3y9s0hx672uylenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsu7l024","addr1qxq8p340clkah7r5xkyegfr8xydcztspsm8ykx3q3au4gqhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsurkrdh","addr1qxavg3u5ccckytdc4xw2f4ewqxk383fth9u79vmtgthvhn8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsecpsum","addr1qypp9eapxm383dvgrzulpnp8gn5sraj3442ece82p0ew2ahenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsj2lucx","addr1qyk30y4sqddtlcyx7pejflvc5hcnlvk93wghz34gpx6v5z8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsjw9334","addr1qyn4tk4qtr824lmryrgmr8sdcr95vdlqutdcz7hfrsauhahenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms6c0shm","addr1q89ymv8kmfygjx7ylnsgduqurccukdwc39we7l84d0wq7u0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsfsd0ma","addr1qyp64rn3tdyc43u7mehruyy0dppldhu3gcde7qqs5u9h3r0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsg4ef4a","addr1q99xgsz0us5x90u552aknyts0jtdv56mpc7tnssxg9e0k28envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsp8upsu","addr1qyajn2gfhf6tegwq747tsnt230u00fgmmdk5nw4g77jfwm8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsuc7gwq","addr1qxdakc3fdnx08vztt5mp26v7vl4k9r9mzpt34dul7w3pad0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms6wluet","addr1q8mv8ulw0rsr0p58c7mwy0mpngupaf34fzeurqfcldlm088envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsyw5czv","addr1q9x3084jguqx7a0ys493gdk33u8rh89wnwpgh7vdpjfvm5henvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmstmm8gs","addr1qx703kcpn7dtvmjhtxah7xpuu3zdpp2ckyd9sdkl5qwxvehenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms58988r","addr1q9unv69p5y2654exkynckp0l97jjq0cwgm8eapdcy8w8fnhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmse0exnx","addr1q9nmm6493dfrs4fjj8lkax9l3qagz3cklpszy64spamtwnhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms2cfe98","addr1qyz0ah9fqq3sjsp2vvkvu8w3u33hf4ecfd309f8fmrc29mhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsz2fw5u","addr1qxthrv5gw2mme5fdwwz7az5er8f2s8k7rmyf7z8u9v353fhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsv39e4u","addr1qys4jtmjy2l25e5ttdteyze7zp95m0c5a4evsr80xc5m9rlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsmhr8vx","addr1q9gtf50vtcwtc42462qg4hvhxpfd50tt8skxulv7cnex048envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsjfgka0","addr1q89pup2kn02j39lh6vd5667nwvefrg6tulgfcsvv0r9phthenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms8yuv43","addr1qyd4a4ndd23e6dcyvk8pnr335wycmj25y2u34taqyvt5778envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsfps5t7","addr1qyhennmw6h4aummyqtc3s5ww7svuhjr0w06lx0lqygp9670envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsp29pcc","addr1qxd9cw86a76hxzprnjzwhqkq7wjhjhc8e0d9yys42tem6dlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsfcwv80","addr1q800lvkw9erzkcw9k20rsen2vjz28j0x5s328h3rxwja4hlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsn24gvf","addr1q9dm3d0rexeu6mwgxf9zlhmtnecf5q7w4sk88tewuw8j8u0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms8wkypm","addr1qypuakhzkufctxcnshc9smpqrsg5jrl9grawdut79qzqcqlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsnqd4mj","addr1q9a0qdh0usuy6kfmcnfky0mspf35kdu5xj93n5l0kc8qp3lenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxeyzgp","addr1qxmjl5g3kt5c79xs5zhwl5jwkua6xl0hcce7nqrc4gk0e8henvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsn7xq48","addr1qx2qrv3drah25wtkxfzd5zqasahewzr2h6kkzq7hnshqj00envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsr5nh6u","addr1qy7a54nj59xje0gqhrh2npc9nuzenl34fr398wx9pdyxjw8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsnpyhw3","addr1qy073s3l654emlyg3nsqfeh30j64ldlrchn3sm70fddwr7henvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms57nkmq","addr1qyua6zv2rqzwx6xjzrk0jq47rmw5d4c7kx8vc4p2f8yyjmlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmswtydtk","addr1qxxkwxf8a37exd0vhlpvmqsl0jhdattscdg7qf6l8tjzxq0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsu7ymw6","addr1q8ylnvc7vr2z00svmg9p8nqtejgkaqsg46jltwhzy895528envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmszns94p","addr1qx3jh908k2aqf9hzu0zfjenn0ccx9cmaupq4urtpvkgrxshenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsdnka29","addr1q82v63eep4j3l8e42zvasmcy7ma36g3lzxyakqh7wwy20z8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsjq099d","addr1q8g87z6k8rdcvtje586n3tqqm2324vyyz85zzqlc2jm7c9henvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxksdef","addr1q9h26w0tweg0rzsf2zk32ml2zlzksktqalvmjugm724kzr8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms8fq0xl","addr1qx028ar6eyvdyxcclym3g0p8r9j3l3zur7jwq78qqg2x2z0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsvlrm7e","addr1q88su5zxc0kexqfd49hn0raznky7urhwqa3cqqkujl5h8xlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsgdlv8c","addr1qxyvx287c00mz405m6q6rskk7jdxuxfak89xjskh55p65e8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxxkkea","addr1q9rylevpkr67qvn9r8xwxnhs7kwckms2np2ea5xk6t5v288envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsl6rh07","addr1qyhuuqrw379pmy4fhyd364hd38frtysfaw9kqgrc2jfgnd8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsk2p7ml","addr1qym30gf8c7nsk335kqg2eg6u9rzkxwg2a69jvyczfevar98envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmskxe2ff","addr1q9mdusdsaq5n4g5k733fznwmd875n48ugtwnc40xfexyrtlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmshfmgfm","addr1qx4wxhgyrehm8006l4xsr4xmunzjnhwq3njwwrhd7cnd0khenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsjjd2w0","addr1qys5yfa8w2nrrxl3s7k93ycn24wnqd8m200as0hdgk6eghlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms3sg7ny","addr1q9pprsj2w8jny0sjexherjfu7n67jlfwjggpj5f6vz3s4vlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmssqps4h","addr1qytv7fzml0njfa43kcxk6pz7spkl3hmlweee6fzsyxj3pxlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmstk77f4","addr1qyhqwvryua74ynlqatwpk26dlgzcp3ht5qm9k8uzphy4sjlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsc60myt","addr1q8ta9puy0rfrfszajez4ejltc3je5zj0rz8v45pnl84v8t8envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsja2cj0","addr1qxs42gdewncum0grs3r8f3ulvjdfj75zctkg42zqhg5k73henvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmskv5l4f","addr1qx4a2ws50mq5xgtd2jkh5qdwmjjhmy88m5gvlgrh04muek0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms4j7yry","addr1qxzv0zwtla3a0y37n7heqhmngdm7sa09wy53jtl7aup3d90envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsz3aqxl","addr1q8wph0lax9xmek4n7hwwx65knzdwpjkyzgvvfjc6t06r4u0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsyjsva8","addr1q9jps68cftdtaw25jyc4y8hlw8qs90qe86xmuvwqzdu2xf0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms5slzgn","addr1qywa5gts9jq96cel5nsn5j75nxjjvj7u4z0kq9utyxjjanlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsp624jp","addr1q9euyayhjklm0tyetjzdjurjwya2h2p6ukw3yncvqc7cuhlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsagllkk","addr1q85tqeyaucywqw5rdepahj4gflp8nclzpw605qhv2npcurhenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms3eyrz5","addr1q9pmjcqg87shr87matrkdx8lcnc499wg9cf0ws9v4dk43nlenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsemk9c2","addr1qyevwe0y4c852etcm4pr3lrufw29ln35nu6un86pyxgkdk0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxverym","addr1qyq887gk0q0cqndqp79e99wvdf0u4zml5s497dgtcv47r7lenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms3yn2ds","addr1qx6gzf3yyhv7m285dwt4u42j9kucqp3q672ehhjdflr3hs0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsy9j5yn","addr1qyug97fltyta9k4h7emv53jdne7uvuax4fyhuts780yuad0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsz5aagj","addr1qxdfdqm5627d0572cf08ju33gnuhmdryegrqfwn7hz76lz0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxjk3t0","addr1qxgyewwfy52g5hqmlvet0lyw6gl99fl07swslq5frq3ek8lenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms3zrvsc","addr1q8yecu66fx8746a5280vrc9dzg965mmghfmvlsrcr3zyv00envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms6cqsnr","addr1q9sz55cfgd5ycutrw9ufq2n04z3cg6w9kmd7jlce5ervsy0envmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhms2unwkm","addr1qxan8vm435xxearey38l22eap8rh0hqler3em3a58mk66ehenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmsxpnlcp","addr1q9rp58sju7zqk2pvsm97k9lplx75qllpjlrv8q69mq5ya6lenvmnavwv0xtlw7998wfk944qq6r6lhq75y03p0myzhmskkdtpu"],"_after_block_height":7900000}' | jq > address_txs_stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32.json

### stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy
echo "Fetching data for address transactions stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"
curl -s -X POST "https://api.koios.rest/api/v0/address_txs" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_addresses":["addr1qx8fyqcexcvu9xvqjuhtg52jge368az2sanwpwjw29qaaxyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqm4tak2","addr1q96dux3gpf099zf3azchntkek0lk2k3fwufrwvjz3fpk6luz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqyznt9m","addr1qxdhn53taqjlguhema5cjlfeekljec4ylc5ejel7n0l0cn5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqc6q2y3","addr1q95rs05utsaehv2guvne9jlrqngj6wce9y49c0kgwj9u7svz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq3fjghd","addr1q9k8qvv89cgwhg23t2wrjjhq77s44skvmrnewpdw7xu0l3uz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqdlwces","addr1qxqa2v78qa4tc4q7zp7f3wd72whrpr5agpe4yrhp8vrekuyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq473e6z","addr1qyecfwyl93ecpa0k8p8008g273qn89mq3dasd4pkachd6xyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqvgc7g9","addr1q9mm7zwzhmf3mwc9t2g9uy0urpeq2un0sznzjqum3utxejuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqj7hywh","addr1q9zy58xs8q0m56h73c3qr7yqlr54kyx9hws2kahj834dqz5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq7cdqul","addr1qypfzwmy6fkz9j64jjvmnjup9jqclxs0yfh90p7739l7l55z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqxp8yrp","addr1q8alh4r6udrz38zqp93jzk6twmw5w73t4z4wv5h28jm7vzvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqatu3zq","addr1q852ftanr7yu3jd96qtqsp4l70eh7v3pm3ydg39c47yvcyyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqka3t7m","addr1q9hm3vnkaadc2s9336zddf6cv4694rytr0u37w5mknwezeuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqnk9aus","addr1q9m5qxx5pwlea0ncrjhfm78nkd8a4mqdvkep3fx32c03xv5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqer9j5k","addr1q8h629wmwaqar4w25dd9eu3l4cxnn6dwp9m0qvpkjne4h3uz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqwfrzfm","addr1q93s2gfctzuqkrajsgfm6thllattq8wx755t3lr94ehfjuvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqwrxcyk","addr1q9ye77c38lc9mytkwrs7z26dtj9y64c6fcx79f9e0q8g3ruz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqncxa5n","addr1qyh6fakqvqfsfs0w9qgchg2hrh77e927vr6wt5sutwnw5huz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq8guve9","addr1q99qxr99rae4ys7f2az5arx5f9433epdcmncydstkr4ylh5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqz03lpg","addr1qxd72hsc7pc4jgh77q2wz0mjtruym3pudth3lfm424twh45z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqggdlmv","addr1q8f9vsuu4qwtjuhsw3e0kz338dkn0kxrdpge2qfel6ptqvvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqh0ruwu","addr1qxhvuc6u7s56uy622cva7dx27keznwg3g4sv2rcl6m3tvwuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqwmxsj3","addr1q9vg8m7q3rxynz7sy3n0acr8cjqlmdy5hpjaunzxmts2mvyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqxcxhp2","addr1q87vpapqxng8scjvlhhpdajg90v6qpwuqvrruexr2hnmayyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqkpn8ja","addr1qyadm54nmc8kr2zdx6fsu7d524ftx3d7e4z4wqlhlf5adqyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqfkcnr8","addr1q9dtnsqdyjkwlsxyz2h05psxyc3wdg6n9y2t2qydxuzxmnyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqxyq22q","addr1q8txupuy24cd2velqmrmgs97j749j7szeg9c7q6t7p6fkyvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq6k2g9v","addr1q8j9325wp9d2mx8zqggqt8eqhhlmvavx9dg6vdgexvjrkyyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqsmmx94","addr1q85agrytsskjmhyzru8lu7z0sphyv0ya3nj4g22fnq9ayzuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqmjek7v","addr1q9s0ch4873natdfas2dv7k3sypz33pyz8ejcwlcq40q3fpyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqzmh8nt","addr1q8yfs9w7q3pvhmlaxnvzve2fk3q5vvjdaucuy3xwz7t05nvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqjep8yd","addr1q92wf9mpw9pxmqjpmm0eks2lk53hs4nqr37uh9hkv6jv7r5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqr4htrf","addr1q8th0y6jrlv4y2edffusfynrtvj3chmjekkq2cg4x5u75n5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqqrugh8","addr1q8qec6wu9et2wp3ldqctvvfg73skm25mp3utcr76tr89265z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq73j6hh","addr1q859przngwxzx9q0nxs0q5xhf5g4vyaeafzefgr53c3yy25z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqjvadyh","addr1q8wf6xmp3eh49zzlmmpnlc0npch37tmcn93r7czg5v5dkl5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqsk87kt","addr1q8lfndnnp946hnf9ngf036nvafuua9ckzu55cgtqazp8g0vz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqtdrwy7","addr1qyadglqdpxj5cz54f5k480sjr5dnhzcyxwlt93eruj6nc3vz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq4jz7pk","addr1qxq4rtqp0xnq2367eg6c9h6yfjdy9g0yu6arlghgvjkfxruz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq47svrx","addr1qxcpgq4a5h3ae0vzdvug7kccvgzlkzyczfmyr4cm5tspme5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqy3ffh2","addr1q9wfpzm96qws2lu98vm8rk6m86jpjhqve6mgu0e0jln99qvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqn5acq7","addr1q8t6sk2gy0lj9mzxr392xv4s9erqs9z8rk0hfy4n4mvlytuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqh8fxhc","addr1qykh7ceypnv4wkfyr6rn645zy7z6xhsdxh3ysppj6lcmgkvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqr7e34n","addr1qxx28mglkkslpm36etx4w84u3auny44mf462mqmanejcskuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq46yh77","addr1qy02yf89jvhv8qx9ulgk35xfwg4qyaa359g563ww8scwl8yz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqp0k2he","addr1qxjpsdnk4wz3wt3fa3kljxwm7yvf3270sal8napkn8tz6r5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgql05uj2","addr1qx9ewqtvvx6965f9k5qf9h73qct67v4xafu8ryhdxyklpduz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqj6xsv9","addr1qxt6q0hcf5yanzdhd8gl9654kzcftfzy9t6j7p0adcf454vz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqst4afu","addr1q9h8a75035jxqshtzryk66tun7p99608c6zfm3xcs5vthquz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqu4lndq","addr1q84436ysk4f664lxu6nc8mgxx8ysmp22khm2hjtjauszamyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqseu57q","addr1q8xycrmscpdky9es88z3hg6dswsscv7f74hwrmz0rzkzl6vz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq3w9l2e","addr1qys0z6qmvz6f83nprdq6z300f2puq4eg2a0unv4pc0yzanyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqpl5z0f","addr1q9hcgqty0ecucnya4kn5mavjqcxrx79c69ndc977jedcttuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqzwpstu","addr1q8qjz5drt7u8utf2zat49ejy9fgz4h2ntw94sx567q6h5tvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqfz8uup","addr1qxkzryss8nygwfz3j6nz4c0s5ts5r4mpnwparccyg3gwkj5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq0j7khu","addr1qxzmj689fxretylvh70pd55j77cnmnj8kq4cxcjm4hakhw5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqnerdhx","addr1q9nk34awn9j0t0ggkp4htm8x4e06kxsz25y0062vkxnj2dyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqar3vs5","addr1q9vrw6ezh6rkwphhk0sprmmhfdqfz2h3v3s4ml3yh3lryauz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqh8ayzx","addr1qyallsln9uva06lm5j3puu6rwmqsx77rgqahc0a9da0hhf5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqs5tcff","addr1qyf9g4wz3ugl4xhqrrl383pmklef3gy5um9cp7adjt8s7u5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq2qyguy","addr1qxglf8avrrfqd0sejgne43v5z4vdsqktm9a7htn85gp44pvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgquk06ry","addr1qxtcffljgkmd92zvxpg9s57usryfk5ug55frthpwdxlqptuz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq44fyhn","addr1q8d37lwchj6qew4nwt28vk46amy77073deay46zf8f9r3a5z68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqc6ehhp","addr1q8sjp978d9wfq00wtcknjl8lnkrhf0xdqdheazawrjvsjmvz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgqfnej3s","addr1qxscmrs2zs6ztzuqllz69gwxtmfrghhmwrprhjcwe74pyeyz68jxz2uxdexn04evgeg44a9u7jyvsu7j8zyw9nm3gcgq6wehsr"],"_after_block_height":7900000}' | jq > address_txs_stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy.json


### stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz
echo "Fetching data for address transactions stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz"
curl -s -X POST "https://api.koios.rest/api/v0/address_txs" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_addresses":["addr1qyc47hv2z7vvmu70kj9ztqju3v67g4vj8c3cz8ws4negqz2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq58ux8n","addr1q903ccp9vczudkf2uxrvf3t2mr0ew9rsyzlppcm6af49m5zkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqjuvt05","addr1q8sl80xd2ygane9ugzxve9ykaxm2rn4mqpv3q79f68g2hejkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqyf3uky","addr1qx2qnquwq6suk9ge3c9ycmpysecg7x0g2gnh7v7r60tk6pjkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq5ck0hn","addr1qxesaxez4vw4shx9phxszlwjl5n9wh3x5p4ydvtfgk33qwzkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqkqrlkw","addr1qypja6h302pnm5wg06gw6za5dswcnregc3969v849glxr3jkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqywn4fl","addr1qymhck2nrfxxzvr7w2xd58ypq8s3fk84hcw34ce9rwu57d6khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqxfq9lh","addr1qyzzg3fr600uel2vdd89pk8lpjkxjsz2skwqza7zvkw88hzkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqncsr2k","addr1qyz30h92lxq3th7kpwcxln0rckh6x00tp8jj2ghxknnegl2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqp0pdjk","addr1qxk8zg3hh2eytd4lgwjr2rfxmqh62ej60w6nemrxujlwx5zkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqvphsw5","addr1q9k6qle58cuy2ny4hq8rvpknm82u57xfmaxzlxk77aqqd7zkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqjatswr","addr1q8eedf9qpqh3t54qnstd732lcqjvxzrgferhvqq0387wlp2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq804kpr","addr1qxtmdx8zq0zvdeyz8j4ut9tz8w3g9k3t7gy6qvfe0wxpmk2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqy2fj92","addr1q90muefae2jd8rhtrhrgtgcg624rx98fn4nmy03ksyda366khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq83zygs","addr1q8vrg9jykn3w5dc2hn0pqtygwu4mvk93x5wxj3nq88m6nt6khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqrt9ans","addr1qypwkn83au8y6ffnm7z0nvchn8supp7qyp4yfp6ua5vqxa2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq0qv4al","addr1qyghk3u3llzgj0uzl5ev6fpq4mjnpl386ggzvvy2ekzvy4zkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqpwhcsx","addr1qx00p9hvqgx76842umz4ne4z05x0akcwdkvuy4raqvz0d92khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqzggxp8","addr1qxx9jsaxrf3v3vdkctqpus6lg9cy3u6wtz6sk74jhjku38jkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqwrfwme","addr1q9v8a6vcfswm29ds5zumnpxv952e0xjjj5mn8yuej82kds2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqdfzdvl","addr1qydllw6gev2x3pxldrf8d9uldnwu8upwlgydlqekg29ffa6khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqmlxcvf","addr1q9km9avsehcmfag98hy0tvqnuqcjdmr0y3qyhgpyhvw9de2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqjgxz90","addr1q8y456g4tlmem4c5g8h4zdpdmw3rftfxcr83w9rzza3g9d6khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqnm829h","addr1qxut7ananr4pftc68lg65ekvx9qv05vg4tmh0azkg6e35u2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeql0knxm","addr1q8sjqv6z6wp946v7lwu6ud5ncf9788nu3x0l9u2sl8jh06jkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeql5yuan","addr1qyqfuurpda2qnf8jqzxxxlhrznwtvc9uca7s6jdfkq8lej2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqgzssdy","addr1qxvay8xj5r8x8ck8qekz5zknh08ht9ely96l6ueemltgk9jkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqwarkeg","addr1qx32uetmenqyay3h7jqxc7lu5xs5w5hqs9xa8ph3wahl9q2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq0m7tpm","addr1qxeau72cz52j9xrccw54alvnn2egp9uzyrkel0wxuhjgy06khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqrznq6t","addr1q8jn7z6nhk6tj5nx7wt3ctj2fdfpm5csgwztxu46frx8qj6khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq63kzqm","addr1q9qrg8swd2vc8xm39rfl53w8mhlg9yf3kkt3czn3lyrkqzjkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqrh0q45","addr1q9p3w82mectj72s4z0h84zyga7d58sgkr3qc9j530qkwy52khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq4z7mmx","addr1q8ysyz75gkvdlj4wqc0cd67my87dagtrp9arxsk2468dtw2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqd4dl6j","addr1qyug082a2ycg4wmjukv9t47xhlyfn5v37dwk9ss3ppzc732khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdequq7dta","addr1q9zh9fatdmjyucfq0kn9pvlkvpmcg5guu0w0m782t8cmqcjkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqw623h9","addr1qy4y9g2fxhewdv7hyypl4qy85pwg4jjeklnwxz3xqyjpgxjkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqkkuhzj","addr1q8npxw7eaf5nmhlemkeghxsh3kz2k5wlfnfg34cts75wlf2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqe405xh","addr1qx9dhnc4mrwg0ku4yxnqhna5crxuf50re7mnku5ys96eemzkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq8qgzsg","addr1qyyerz582fkuu4r3z9ml6lapyeuevlf62f0rkwk9ncyh8y2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqwlvgkx","addr1qy5adle604apsc2kavh6sj8xas75h5cr7dzvgpanmh276s2khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeqyneku0","addr1qyuvv64urw37fgeddekquv37afwsfgv6wxmxt7sm47c2742khyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdequpy7jn","addr1q9c6cgena5wp5dhnk9583pmlf08jv2w44rkezmgtd9ls8yzkhyyhw3v5fudffs2nr4rm5crshwcled75mfhwkvcetdeq7g28qe"],"_after_block_height":7900000}' | jq > address_txs_stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz.json

### stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr
echo "Fetching data for address transactions stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"
curl -s -X POST "https://api.koios.rest/api/v0/address_txs" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_addresses":["addr1q924ga6skag2xj75u6fhnnh9nvzklecgytdgwlwcy9sevu87jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s74tk5s","addr1q8ctprzrqzap07uxemfq80ru4jqude5tuxglhkyt5wmy0kh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69sh6qryx","addr1q98cuvdld6q3zyekhgpp2fjt47sm7rw98gmtttsekzfz6wl7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s9atrfg","addr1q9js3gx0r0wnu2z837jsa2e8a6fz400wt3q04c6s9c4w6m07jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69sv475wl","addr1qxrcrpa22z2ndeydh838nld2ldcsf9y2mqpas24t8dcpws87jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69scf0pjj","addr1qyzxyf9nd56n5m5q44y0y5kj9vmg3dyh2xrx55wc7vvsnzh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69syllpdy","addr1qxwhkr2t9kf6k2svj47ya7hwdlw9hx6gwdyfefmfzjghwdl7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s8xzkh7","addr1q8ct2vw7fsak50khxhweklh0vk2tm8n2lx86nlmdkravj8h7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s6s4g3n","addr1q8xaxj0kzkds0a08nlycu3p8c6dllkn6xeyql23dquhpqkh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69swfrs5t","addr1qx0stwwpjmk94e657c8rqflwq0ff0jpt2c8cj28r6pnl4fh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69szmsxgq","addr1qxsr2uy22t48rgy3nrnad4eeepqccsudvl90xknd507yzg07jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69svkgz2m","addr1q9n7465wlvvj2ehuks84ykqj0z9gd3uqyj94upxrwcf732l7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69ss0yuv9","addr1q8t26uv5rax0ym9shh5r6xthwgemm63kr8erq6hmfku88e07jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69snz7n94","addr1q9wf0ayz7h7a7dwdr7ec6jnmed34fudxh4k2jyazv8c4hs87jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69ssm0602","addr1q87jp2wfzmt55js88056aket8rw8knpzv960zknajcezsvh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69sy34e40","addr1q9n8ws5eh8lgd5uutq36vxlngj9gcmfknlems3u9upuu5kh7jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s5jvq4v","addr1q8ekg0jj9d4ecedug6rge9engn8wvuqwwhy5d8xel245vj07jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69s2yu9rh","addr1q9velr05v9w6lv2lsr5crzhweyw3ugwgfms6xhtqdhxnrd87jjzm20pp2j5rj78009tx4jmvsalz0p3772j2ntzgl69swv4c3r"],"_after_block_height":7900000}' > address_txs_stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr.json

## Fetching pool information
echo "Fetching data for pool information"
curl -s -X POST "https://api.koios.rest/api/v0/pool_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_pool_bech32_ids":["pool1rthy0xp2syng0cydp85wvz973szmq2ns8u5p4hdedkwlyhry27w","pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv","pool10q33p4hx4wqum6thmglw7z5l2vaay4w6m5cdq8fnurw7vjdppcf","pool17e4rdh59t4fmn4g3p02xvs853katrjzge830tsmd3sfdc645yvt","pool1f6lnuxzw90mmd399nxqjvzyyxgmf4h3cp7j6pp5s7xmps86arct"]}' | jq > pool_information.json


echo "Fetching data for transactions info"
ALL_TX_HASHES=$(for i in `ls address_txs_*.json`; do cat $i | jq -r '"\""+.[].tx_hash + "\","'; done)
ALL_TX_HASHES=$(echo ${ALL_TX_HASHES} | sed 's/.$//')
curl -s -X POST "https://api.koios.rest/api/v0/tx_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d "{\"_tx_hashes\":[${ALL_TX_HASHES}]}" | jq > txs_info.json

echo "Fetching all assets"
for asset in `grep -A1 policy_id txs_info.json  | \
  awk -vFS=":" '{print $2}' | \
  awk -vFS="\n" -vRS="\n\n" '{gsub(" ", "", $0); gsub("\"", "", $0); gsub(",", "", $0); print "_asset_policy="$1 "&_asset_name=" $2}' \
  | sort | uniq`; do
    asset_policy_name=`echo -n $asset | awk '{gsub("_asset_policy=", "", $0); gsub("&_asset_name=", "_", $0); print $0}'`
    if [ -f "assets/asset_${asset_policy_name}.json" ]
    then
      echo "Asset ${asset_policy_name} already existing"
    else
      echo "Fetching asset ${asset_policy_name}"
      curl -s -X GET "https://api.koios.rest/api/v0/asset_info?${asset}" \
        -H "Accept: application/json" | jq > "assets/asset_${asset_policy_name}.json"
    fi
done