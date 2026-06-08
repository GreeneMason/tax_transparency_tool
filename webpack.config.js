/*
 * GovLens Frontend Build Pipeline
 * 
 * This configuration bundles and optimizes frontend assets for production:
 * - Minifies JavaScript and CSS
 * - Adds content-hash filenames for cache busting
 * - Splits vendor code into separate chunks
 * - Compresses assets (gzip recommended at CDN layer)
 *
 * Usage:
 *   npm run build       (production minified bundle)
 *   npm run dev         (development watch mode)
 *   npm run clean       (remove build artifacts)
 */

const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const { CleanWebpackPlugin } = require('clean-webpack-plugin');

module.exports = {
  entry: {
    main: './src/main/frontend/js/app.js'
  },
  
  output: {
    path: path.resolve(__dirname, 'src/main/resources/static'),
    filename: '[name].[contenthash:8].js',
    chunkFilename: '[name].[contenthash:8].chunk.js',
    clean: true,
    publicPath: '/'
  },
  
  mode: 'production',
  
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env'],
            plugins: ['@babel/plugin-proposal-nullish-coalescing-operator']
          }
        }
      },
      {
        test: /\.css$/,
        use: [
          MiniCssExtractPlugin.loader,
          'css-loader'
        ]
      }
    ]
  },
  
  plugins: [
    new CleanWebpackPlugin({
      cleanOnceBeforeBuildPatterns: ['src/main/resources/static/*', '!src/main/resources/static/.gitkeep']
    }),
    new MiniCssExtractPlugin({
      filename: '[name].[contenthash:8].css'
    })
  ],
  
  optimization: {
    minimize: true,
    minimizer: [
      new TerserPlugin({
        terserOptions: {
          compress: {
            drop_console: false
          },
          mangle: true,
          output: {
            comments: false
          }
        },
        extractComments: false
      }),
      new CssMinimizerPlugin()
    ],
    splitChunks: {
      chunks: 'all',
      maxInitialRequests: 5,
      maxAsyncRequests: 5,
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          priority: 10,
          reuseExistingChunk: true,
          enforce: true
        }
      }
    },
    runtimeChunk: 'single'
  },
  
  devtool: process.env.NODE_ENV === 'development' ? 'source-map' : false,
  
  performance: {
    maxEntrypointSize: 512000,
    maxAssetSize: 512000,
    hints: 'warning'
  }
};
