Vue.component("footBar", {
  template: `
    <div class="foot">
    <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
      <div class="foot-view"><i class="el-icon-s-home"></i></div>
      <div class="foot-text">首页</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)">
      <div class="foot-view"><i class="el-icon-place"></i></div>
      <div class="foot-text">附近</div>
    </div>
    <div class="foot-box" @click="toPage(0)">
      <div class="add-btn xl-add"><i class="el-icon-plus"></i></div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)">
      <div class="foot-view"><i class="el-icon-magic-stick"></i></div>
      <div class="foot-text">AI 助手</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
      <div class="foot-view"><i class="el-icon-user"></i></div>
      <div class="foot-text">我的</div>
    </div>
  </div>
  `,
  props: ['activeBtn'],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html"
      } else if (i === 1) {
        location.href = "/"
      } else if (i === 2) {
        location.href = "/shop-list.html?type=1&name=" + encodeURIComponent("美食")
      } else if (i === 3) {
        location.href = "/skills.html"
      } else if (i === 4) {
        location.href = "/info.html"
      }
    }
  }
})
