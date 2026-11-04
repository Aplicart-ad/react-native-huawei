module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.aplicart.huawei.HuaweiPackage;',
        packageInstance: 'new HuaweiPackage()',
      },
      ios: null,
    },
  },
};
